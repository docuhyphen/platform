package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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

    private fun enabledConfig(): AuditArchiveConfigService = mock<AuditArchiveConfigService>().also {
        whenever(it.isArchiveEnabled()).thenReturn(true)
        whenever(it.getReverifyAfterHours()).thenReturn(24)
    }
}
