package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** In-memory [AuditArchiveStorage] test double: refuses overwrite, same contract as the real implementations. */
class InMemoryAuditArchiveStorage : AuditArchiveStorage
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
 * Phase 4 gate for [AuditArchiver]:
 *  - a stream with fewer events than the configured segment size is left open (not closed).
 *  - closing a segment uploads a segment content object and a signed manifest object, and
 *    persists an [com.docuhyphen.app.api.model.entity.AuditArchiveSegment] row whose
 *    `merkleRoot`/`segmentDigest` match what [AuditArchiveVerifier] independently recomputes.
 *  - closing a second segment for the same stream links `prevSegmentDigest` to the first
 *    segment's `segmentDigest` (the segment-to-segment chain / boundary checkpoint).
 */
class AuditArchiverTest
{
    private fun ledgerEvent(streamId: String, sequence: Long, eventHash: String = "hash-$sequence"): AuditLedgerEvent =
        AuditLedgerEvent().apply {
            eventId = UUID.randomUUID()
            eventTypeKey = "exchange.lifecycle.rescinded"
            category = "EXCHANGE"
            outcome = "SUCCESS"
            schemaVersion = 4
            occurredAt = Timestamp.from(Instant.parse("2026-01-15T10:00:00Z"))
            recordedAt = Timestamp.from(Instant.parse("2026-01-15T10:00:00Z"))
            this.streamId = streamId
            streamSequence = sequence
            actorKind = "HUMAN"
            this.eventHash = MerkleTree.sha256Hex(eventHash.toByteArray())
        }

    private fun configService(segmentSize: Int, tempDir: Path): AuditArchiveConfigService
    {
        val config = mock<AuditArchiveConfigService>()
        whenever(config.getSegmentSize()).thenReturn(segmentSize)
        whenever(config.getLocalSigningDirectory()).thenReturn(tempDir.resolve("keys").toString())
        return config
    }

    @Test
    fun `a stream with fewer events than the segment size is left open`(@TempDir tempDir: Path)
    {
        val streamId = "org-1:2026-01"
        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(listOf(ledgerEvent(streamId, 1)))

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(null)

        val config = configService(segmentSize = 5, tempDir = tempDir)
        val archiver = AuditArchiver(ledgerRepo, segmentRepo, InMemoryAuditArchiveStorage(), LocalAuditArchiveSigningKeyProvider(config), config)

        val closed = archiver.closeSegmentIfReady(streamId, force = false)

        assertFalse(closed)
    }

    @Test
    fun `closing a segment uploads a signed manifest that AuditArchiveVerifier accepts`(@TempDir tempDir: Path)
    {
        val streamId = "org-1:2026-01"
        val events = (1L..3L).map { ledgerEvent(streamId, it) }

        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(events)

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(null)
        whenever(segmentRepo.insert(any())).thenAnswer { it.getArgument(0) }

        val storage = InMemoryAuditArchiveStorage()
        val config = configService(segmentSize = 3, tempDir = tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val archiver = AuditArchiver(ledgerRepo, segmentRepo, storage, signingProvider, config)

        val closed = archiver.closeSegmentIfReady(streamId, force = false)
        assertTrue(closed)

        val segmentCaptor = org.mockito.kotlin.argumentCaptor<com.docuhyphen.app.api.model.entity.AuditArchiveSegment>()
        org.mockito.kotlin.verify(segmentRepo).insert(segmentCaptor.capture())
        val segment = segmentCaptor.firstValue

        assertEquals(1L, segment.firstSequence)
        assertEquals(3L, segment.lastSequence)
        assertEquals(3, segment.eventCount)
        assertNull(segment.prevSegmentDigest)
        assertEquals(MerkleTree.computeRoot(events.map { it.eventHash }), segment.merkleRoot)

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val result = verifier.verifySegment(segment)
        assertTrue(result.valid, result.note)
    }

    @Test
    fun `closing a second segment links prevSegmentDigest to the first segment's segmentDigest`(@TempDir tempDir: Path)
    {
        val streamId = "org-1:2026-01"
        val firstBatch = (1L..2L).map { ledgerEvent(streamId, it) }
        val secondBatch = (3L..4L).map { ledgerEvent(streamId, it) }

        val ledgerRepo = mock<AuditLedgerEventRepository>()
        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.insert(any())).thenAnswer { it.getArgument(0) }

        val storage = InMemoryAuditArchiveStorage()
        val config = configService(segmentSize = 2, tempDir = tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val archiver = AuditArchiver(ledgerRepo, segmentRepo, storage, signingProvider, config)

        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(firstBatch)
        whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(null)
        archiver.closeSegmentIfReady(streamId, force = false)

        val firstCaptor = org.mockito.kotlin.argumentCaptor<com.docuhyphen.app.api.model.entity.AuditArchiveSegment>()
        org.mockito.kotlin.verify(segmentRepo).insert(firstCaptor.capture())
        val firstSegment = firstCaptor.firstValue

        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(firstBatch + secondBatch)
        whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(firstSegment)
        archiver.closeSegmentIfReady(streamId, force = false)

        val allCaptor = org.mockito.kotlin.argumentCaptor<com.docuhyphen.app.api.model.entity.AuditArchiveSegment>()
        org.mockito.kotlin.verify(segmentRepo, org.mockito.kotlin.times(2)).insert(allCaptor.capture())
        val secondSegment = allCaptor.secondValue

        assertEquals(firstSegment.segmentDigest, secondSegment.prevSegmentDigest)
        assertEquals(3L, secondSegment.firstSequence)
        assertEquals(4L, secondSegment.lastSequence)
    }
}
