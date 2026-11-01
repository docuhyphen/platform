package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.model.audit.archive.SegmentCloseResult
import com.docuhyphen.app.api.model.audit.archive.SegmentVerificationResult
import com.docuhyphen.app.api.model.audit.archive.StreamChainVerificationResult
import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class AuditArchiveSchedulerTest
{
    @Test
    fun `repeated archive ticks close only newly eligible ranges`()
    {
        val archiver = mock<AuditArchiver>()
        whenever(archiver.closeReadySegments()).thenReturn(
            SegmentCloseResult(closed = 2, skipped = 0, failed = 0),
            SegmentCloseResult(closed = 0, skipped = 2, failed = 0),
        )
        val config = enabledConfig()
        val scheduler = AuditArchiveScheduler(archiver, mock(), config, mock())

        scheduler.archiveTick()
        scheduler.archiveTick()

        verify(archiver, times(2)).closeReadySegments()
    }

    @Test
    fun `repeated verification ticks do not invent verification work`()
    {
        val verifier = mock<AuditArchiveVerifier>()
        whenever(verifier.verifyDueSegments(24)).thenReturn(emptyList())
        val scheduler = AuditArchiveScheduler(mock(), verifier, enabledConfig(), mock<AuditRecorder>())

        scheduler.verifyTick()
        scheduler.verifyTick()

        verify(verifier, times(2)).verifyDueSegments(24)
    }

    @Test
    fun `verification records each segment under its stream's owner and one failed record does not stop the rest`() {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val verifier = mock<AuditArchiveVerifier>()
        whenever(verifier.verifyDueSegments(24)).thenReturn(
            listOf(
                segment("user:$userId:2026-10") to SegmentVerificationResult(true, "verified"),
                segment("$organizationId:2026-10") to SegmentVerificationResult(false, "digest mismatch"),
                segment("platform:2026-10") to SegmentVerificationResult(true, "verified"),
            ),
        )
        whenever(verifier.verifyStreamChain(any())).thenReturn(StreamChainVerificationResult(true, "verified", 1))
        val recorder = mock<AuditRecorder>()
        whenever(recorder.record(any())).thenAnswer { invocation ->
            val draft = invocation.getArgument<AuditEventDraft>(0)
            if (draft.owner is AuditOwnerScope.Personal) throw IllegalStateException("The audit event could not be stored")
            AuditCaptureResult.Captured(UUID.randomUUID(), draft.eventId)
        }
        val scheduler = AuditArchiveScheduler(mock(), verifier, enabledConfig(), recorder)

        scheduler.verifyTick()

        val drafts = argumentCaptor<AuditEventDraft>()
        verify(recorder, times(3)).record(drafts.capture())
        assertEquals(
            listOf(
                AuditOwnerScope.Personal(userId),
                AuditOwnerScope.Organization(organizationId),
                AuditOwnerScope.Platform
            ),
            drafts.allValues.map { it.owner },
        )
        verify(verifier, times(3)).verifyStreamChain(any())
    }

    private fun segment(streamId: String): AuditArchiveSegment = AuditArchiveSegment().also {
        it.streamId = streamId
        it.firstSequence = 1
        it.lastSequence = 10
    }

    private fun enabledConfig(): AuditArchiveConfigService = mock<AuditArchiveConfigService>().also {
        whenever(it.isArchiveEnabled()).thenReturn(true)
        whenever(it.getReverifyAfterHours()).thenReturn(24)
    }
}
