package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.audit.archive.SegmentVerificationResult
import com.docuhyphen.app.api.model.audit.archive.StreamChainVerificationResult
import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.audit.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveVerifier
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AuditIntegrityServiceTest
{
    @Test
    fun `integrity refuses ledger owner columns inconsistent with the protected personal stream`() {
        val owner = java.util.UUID.randomUUID()
        val stream = "user:$owner:2026-01"
        val event = AuditLedgerEvent().apply {
            streamId = stream
            streamSequence = 1
            ownerType = "USER"
            ownerId = owner
        }
        val ledger = mock<AuditLedgerEventRepository>()
        whenever(ledger.findLatestByStream(stream)).thenReturn(event)
        whenever(ledger.findByStreamOrderBySequence(stream)).thenReturn(listOf(event))
        val segment = AuditArchiveSegment().apply { streamId = stream; firstSequence = 1; lastSequence = 1 }
        val segments = mock<AuditArchiveSegmentRepository>()
        whenever(segments.findByStreamOrderBySequence(stream)).thenReturn(listOf(segment))
        val verifier = mock<AuditArchiveVerifier>()
        whenever(verifier.verifyStreamChain(stream)).thenReturn(StreamChainVerificationResult(true, "valid", 1))
        whenever(verifier.verifySegment(any())).thenReturn(SegmentVerificationResult(true, "valid"))
        val checker = service(ledger, segments, verifier)
        assertTrue(checker.checkStream(stream).chainValid)
        event.ownerId = java.util.UUID.randomUUID()
        assertFalse(checker.checkStream(stream).chainValid)
    }

    @Test
    fun `a ledger stream with no archive segments is invalid`()
    {
        val ledgerRepository = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepository.findLatestByStream("stream-1")).thenReturn(ledgerEvent(1))
        val segmentRepository = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepository.findByStreamOrderBySequence("stream-1")).thenReturn(emptyList())
        val verifier = mock<AuditArchiveVerifier>()
        whenever(verifier.verifyStreamChain("stream-1"))
            .thenReturn(StreamChainVerificationResult(false, "stream has no archived segments", 0))

        val report = service(ledgerRepository, segmentRepository, verifier).checkStream("stream-1")

        assertFalse(report.chainValid)
    }

    @Test
    fun `a valid segment chain that stops before the ledger head is invalid`()
    {
        val ledgerRepository = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepository.findLatestByStream("stream-1")).thenReturn(ledgerEvent(5))
        val segment = AuditArchiveSegment().apply {
            streamId = "stream-1"
            firstSequence = 1
            lastSequence = 3
        }
        val segmentRepository = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepository.findByStreamOrderBySequence("stream-1")).thenReturn(listOf(segment))
        val verifier = mock<AuditArchiveVerifier>()
        whenever(verifier.verifyStreamChain("stream-1"))
            .thenReturn(StreamChainVerificationResult(true, "valid", 1))
        whenever(verifier.verifySegment(any())).thenReturn(SegmentVerificationResult(true, "valid"))

        val report = service(ledgerRepository, segmentRepository, verifier).checkStream("stream-1")

        assertFalse(report.chainValid)
        assertTrue(report.chainNote.contains("ledger ends at 5"))
    }

    private fun service(
        ledgerRepository: AuditLedgerEventRepository,
        segmentRepository: AuditArchiveSegmentRepository,
        verifier: AuditArchiveVerifier,
    ) = AuditIntegrityService(ledgerRepository, segmentRepository, verifier, mock<AuditRecorder>(), mock())

    private fun ledgerEvent(sequence: Long) = AuditLedgerEvent().apply {
        streamId = "stream-1"
        streamSequence = sequence
    }
}
