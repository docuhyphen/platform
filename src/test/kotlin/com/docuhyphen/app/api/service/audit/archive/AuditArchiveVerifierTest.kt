package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

/**
 * Phase 4 gate for [AuditArchiveVerifier]:
 *  - a genuine (untampered) segment verifies successfully end to end (merkle root, segment
 *    digest, manifest signature).
 *  - mutating the archived segment content, the archived manifest, or the signature all break
 *    verification independently.
 *  - deleting/reordering a segment in a stream's chain is detected via [AuditArchiveVerifier.verifyStreamChain]
 *    even when every individual segment still verifies in isolation.
 */
class AuditArchiveVerifierTest
{
    private fun ledgerEvent(streamId: String, sequence: Long): AuditLedgerEvent = AuditLedgerEvent().apply {
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
        eventHash = MerkleTree.sha256Hex("event-$sequence".toByteArray())
    }

    private fun configService(segmentSize: Int, tempDir: Path): AuditArchiveConfigService
    {
        val config = mock<AuditArchiveConfigService>()
        whenever(config.getSegmentSize()).thenReturn(segmentSize)
        whenever(config.getLocalSigningDirectory()).thenReturn(tempDir.resolve("keys").toString())
        return config
    }

    private fun archiveOneSegment(streamId: String, tempDir: Path, storage: InMemoryAuditArchiveStorage): Triple<AuditArchiveSegment, AuditArchiveSegmentRepository, AuditArchiveSigningKeyProvider>
    {
        val events = (1L..3L).map { ledgerEvent(streamId, it) }
        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(events)

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(null)
        whenever(segmentRepo.insert(any())).thenAnswer { it.getArgument(0) }

        val config = configService(segmentSize = 3, tempDir = tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val archiver = AuditArchiver(ledgerRepo, segmentRepo, storage, signingProvider, config)
        archiver.closeSegmentIfReady(streamId, force = false)

        val captor = argumentCaptor<AuditArchiveSegment>()
        verify(segmentRepo).insert(captor.capture())
        return Triple(captor.firstValue, segmentRepo, signingProvider)
    }

    @Test
    fun `a genuine segment verifies successfully`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment("org-1:2026-01", tempDir, storage)

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val result = verifier.verifySegment(segment)

        assertTrue(result.valid, result.note)
    }

    @Test
    fun `mutating the archived segment content breaks verification`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment("org-1:2026-01", tempDir, storage)

        val originalHash = MerkleTree.sha256Hex("event-1".toByteArray())
        val tamperedHash = MerkleTree.sha256Hex("event-1-TAMPERED".toByteArray())
        val tamperedContent = String(storage.objects[segment.segmentObjectKey]!!, StandardCharsets.UTF_8)
            .replace(originalHash, tamperedHash)
        storage.objects[segment.segmentObjectKey] = tamperedContent.toByteArray(StandardCharsets.UTF_8)

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val result = verifier.verifySegment(segment)

        assertFalse(result.valid)
    }

    @Test
    fun `mutating the archived manifest breaks signature verification`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment("org-1:2026-01", tempDir, storage)

        val tamperedManifest = String(storage.objects[segment.manifestObjectKey]!!, StandardCharsets.UTF_8)
            .replace(segment.merkleRoot, "0".repeat(segment.merkleRoot.length))
        storage.objects[segment.manifestObjectKey] = tamperedManifest.toByteArray(StandardCharsets.UTF_8)

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val result = verifier.verifySegment(segment)

        assertFalse(result.valid)
    }

    @Test
    fun `a missing archived object is reported as a verification failure, not an exception`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment("org-1:2026-01", tempDir, storage)
        storage.objects.remove(segment.segmentObjectKey)

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val result = verifier.verifySegment(segment)

        assertFalse(result.valid)
    }

    @Test
    fun `verifyStreamChain detects a deleted segment even though the remaining segments individually still hash correctly`()
    {
        val streamId = "org-1:2026-01"
        val segmentA = AuditArchiveSegment().apply {
            this.streamId = streamId; firstSequence = 1; lastSequence = 3; segmentDigest = "digest-a"; prevSegmentDigest = null
        }
        val segmentB = AuditArchiveSegment().apply {
            this.streamId = streamId; firstSequence = 4; lastSequence = 6; segmentDigest = "digest-b"; prevSegmentDigest = "digest-a"
        }
        // segmentC's prevSegmentDigest should chain from segmentB, but here we simulate segmentB
        // having been deleted from the DB: segmentC's prevSegmentDigest still says "digest-b",
        // which no longer matches any segment actually present in the (segmentA, segmentC) list.
        val segmentC = AuditArchiveSegment().apply {
            this.streamId = streamId; firstSequence = 7; lastSequence = 9; segmentDigest = "digest-c"; prevSegmentDigest = "digest-b"
        }

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenReturn(listOf(segmentA, segmentC))

        val verifier = AuditArchiveVerifier(segmentRepo, mock(), mock())
        val result = verifier.verifyStreamChain(streamId)

        assertFalse(result.valid)
    }

    @Test
    fun `verifyStreamChain accepts a contiguous, correctly-linked chain`()
    {
        val streamId = "org-1:2026-01"
        val segmentA = AuditArchiveSegment().apply {
            this.streamId = streamId; firstSequence = 1; lastSequence = 3; segmentDigest = "digest-a"; prevSegmentDigest = null
        }
        val segmentB = AuditArchiveSegment().apply {
            this.streamId = streamId; firstSequence = 4; lastSequence = 6; segmentDigest = "digest-b"; prevSegmentDigest = "digest-a"
        }

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenReturn(listOf(segmentA, segmentB))

        val verifier = AuditArchiveVerifier(segmentRepo, mock(), mock())
        val result = verifier.verifyStreamChain(streamId)

        assertTrue(result.valid, result.note)
        assertEquals(2, result.segmentsChecked)
    }
}
