package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveVerifier
import com.docuhyphen.app.api.service.audit.archive.SegmentVerificationResult
import com.docuhyphen.app.api.service.audit.archive.StreamChainVerificationResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AuditIntegrityServiceTest
{
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
    ) = AuditIntegrityService(ledgerRepository, segmentRepository, verifier, mock<AuditRecorder>())

    private fun ledgerEvent(sequence: Long) = AuditLedgerEvent().apply {
        streamId = "stream-1"
        streamSequence = sequence
    }
}
