package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportStatus
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.AuditExportRepository
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveObjectAlreadyExistsException
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveObjectNotFoundException
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveStorage
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveVerifier
import com.docuhyphen.app.api.service.audit.archive.AuditArchiver
import com.docuhyphen.app.api.service.audit.archive.LocalAuditArchiveSigningKeyProvider
import com.docuhyphen.app.api.service.audit.archive.MerkleTree
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream

/** In-memory [AuditArchiveStorage] test double, same contract as [com.docuhyphen.app.api.service.audit.archive.InMemoryAuditArchiveStorage]. */
private class InMemoryStorage : AuditArchiveStorage
{
    val objects: MutableMap<String, ByteArray> = mutableMapOf()

    override fun putObject(key: String, bytes: ByteArray)
    {
        if (objects.containsKey(key)) throw AuditArchiveObjectAlreadyExistsException(key)
        objects[key] = bytes
    }

    override fun getObject(key: String): ByteArray = objects[key] ?: throw AuditArchiveObjectNotFoundException(key)

    override fun objectExists(key: String): Boolean = objects.containsKey(key)
}

/**
 * Phase 6 gate for [AuditExportBuilder] ("A range does not verify merely because inner events
 * link; boundary checkpoints are required"):
 *  - a genuinely archived, unbroken stream produces a `READY` export whose bundle round-trips
 *    (manifest hash, detached signature, per-stream integrity report all present and consistent).
 *  - a stream whose segment chain has been broken (a deleted middle segment) fails
 *    [AuditExportBuilder.build] with [AuditExportIntegrityFailedException] and never reaches
 *    `READY` - even though the two remaining segments each still individually hash correctly.
 */
class AuditExportBuilderTest
{
    private fun ledgerEvent(streamId: String, sequence: Long, organizationId: UUID): AuditLedgerEvent = AuditLedgerEvent().apply {
        eventId = UUID.randomUUID()
        eventTypeKey = "exchange.lifecycle.rescinded"
        category = "EXCHANGE"
        outcome = "SUCCESS"
        schemaVersion = 4
        occurredAt = Timestamp.from(Instant.parse("2026-01-15T10:00:00Z").plusSeconds(sequence))
        recordedAt = Timestamp.from(Instant.parse("2026-01-15T10:00:00Z"))
        this.streamId = streamId
        streamSequence = sequence
        actorKind = "HUMAN"
        this.organizationId = organizationId
        payloadJson = "{}"
        eventHash = MerkleTree.sha256Hex("event-$sequence".toByteArray())
    }

    private fun archiveConfig(segmentSize: Int, tempDir: Path): AuditArchiveConfigService
    {
        val config = mock<AuditArchiveConfigService>()
        whenever(config.getSegmentSize()).thenReturn(segmentSize)
        whenever(config.getLocalSigningDirectory()).thenReturn(tempDir.resolve("keys").toString())
        return config
    }

    /** Archives every 3 events into their own segment (2 segments for 6 events), returning the closed segments in order. */
    private fun archiveSegments(
        streamId: String,
        events: List<AuditLedgerEvent>,
        segmentSize: Int,
        tempDir: Path,
        storage: AuditArchiveStorage,
    ): Pair<List<AuditArchiveSegment>, AuditArchiveSegmentRepository>
    {
        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.insert(any())).thenAnswer { it.getArgument(0) }
        val config = archiveConfig(segmentSize, tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(events)
        val archiver = AuditArchiver(ledgerRepo, segmentRepo, storage, signingProvider, config)

        val closed = mutableListOf<AuditArchiveSegment>()
        var latest: AuditArchiveSegment? = null
        while (true)
        {
            whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(latest)
            val didClose = archiver.closeSegmentIfReady(streamId, force = false)
            if (!didClose) break

            val captor = argumentCaptor<AuditArchiveSegment>()
            verify(segmentRepo, org.mockito.kotlin.times(closed.size + 1)).insert(captor.capture())
            latest = captor.lastValue
            closed += latest
        }
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenReturn(closed)
        return closed to segmentRepo
    }

    private fun builder(
        streamId: String,
        organizationId: UUID,
        events: List<AuditLedgerEvent>,
        segmentRepo: AuditArchiveSegmentRepository,
        storage: AuditArchiveStorage,
        tempDir: Path,
        exportRepo: AuditExportRepository,
    ): AuditExportBuilder
    {
        val ledgerEventRepo = mock<AuditLedgerEventRepository>()
        whenever(
            ledgerEventRepo.findDistinctStreamIdsForExport(any(), any(), any(), any()),
        ).thenReturn(listOf(streamId))
        whenever(
            ledgerEventRepo.findForExport(any(), any(), any(), any(), any()),
        ).thenReturn(events)

        val config = archiveConfig(3, tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val auditRecorder = mock<AuditRecorder>()
        val integrityService = AuditIntegrityService(ledgerEventRepo, segmentRepo, verifier, auditRecorder)

        whenever(exportRepo.update(any())).thenAnswer { it.getArgument(0) }

        return AuditExportBuilder(ledgerEventRepo, exportRepo, integrityService, storage, signingProvider)
    }

    private fun exportFor(organizationId: UUID): AuditExport = AuditExport().apply {
        id = UUID.randomUUID()
        this.organizationId = organizationId
        requestedByUserId = UUID.randomUUID()
        categoriesCsv = "EXCHANGE"
        occurredAfter = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        occurredBefore = Timestamp.from(Instant.parse("2026-02-01T00:00:00Z"))
        purpose = "regulator inquiry"
        status = AuditExportStatus.BUILDING
    }

    @Test
    fun `a genuinely unbroken stream produces a READY export with a self-verifiable bundle`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val streamId = "$organizationId:2026-01"
        val events = (1L..6L).map { ledgerEvent(streamId, it, organizationId) }
        val storage = InMemoryStorage()
        val (segments, segmentRepo) = archiveSegments(streamId, events, segmentSize = 3, tempDir = tempDir, storage = storage)
        assertEquals(2, segments.size)

        val exportRepo = mock<AuditExportRepository>()
        val builder = builder(streamId, organizationId, events, segmentRepo, storage, tempDir, exportRepo)
        val export = exportFor(organizationId)

        val result = builder.build(export, downloadLifetimeHours = 72)

        assertEquals(6, result.eventCount)
        assertEquals(AuditExportStatus.READY, export.status)
        assertNotNull(export.bundleObjectKey)
        assertEquals(result.bundleObjectKey, export.bundleObjectKey)
        assertTrue(storage.objectExists(result.bundleObjectKey))

        val bundleBytes = storage.getObject(result.bundleObjectKey)
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bundleBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null)
            {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        assertTrue(entries.containsKey("manifest.json"))
        assertTrue(entries.containsKey("events.jsonl"))
        assertTrue(entries.containsKey("events.csv"))
        assertTrue(entries.containsKey("integrity.json"))
        assertTrue(entries.containsKey("signature.json"))
        assertTrue(entries.containsKey("verify.py"))
        assertTrue(entries.containsKey("README.txt"))

        val manifestBytes = entries.getValue("manifest.json")
        assertEquals(MerkleTree.sha256Hex(manifestBytes), export.bundleDigest)

        val eventLines = String(entries.getValue("events.jsonl"), StandardCharsets.UTF_8).lines().filter { it.isNotBlank() }
        assertEquals(6, eventLines.size)
    }

    @Test
    fun `a broken segment chain fails the build even though the remaining segments individually still hash correctly`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val streamId = "$organizationId:2026-01"
        val events = (1L..9L).map { ledgerEvent(streamId, it, organizationId) }
        val storage = InMemoryStorage()
        val (segments, segmentRepo) = archiveSegments(streamId, events, segmentSize = 3, tempDir = tempDir, storage = storage)
        assertEquals(3, segments.size)

        // Simulate a deleted middle segment: the remaining two segments both still verify their
        // own content/signature in isolation, but the chain from segment 1 to segment 3 is broken.
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenReturn(listOf(segments[0], segments[2]))

        val exportRepo = mock<AuditExportRepository>()
        val builder = builder(streamId, organizationId, events, segmentRepo, storage, tempDir, exportRepo)
        val export = exportFor(organizationId)

        assertThrows(AuditExportIntegrityFailedException::class.java) {
            builder.build(export, downloadLifetimeHours = 72)
        }
        assertEquals(AuditExportStatus.BUILDING, export.status)
        assertTrue(!storage.objectExists("exports/${export.id}/bundle.zip"))
    }
}
