package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportStatus
import com.docuhyphen.app.api.repository.AuditExportRepository
import com.docuhyphen.app.api.service.config.AuditExportConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.times
import java.util.UUID

/**
 * Verifies [AuditExportScheduler.buildTick]'s multi-worker build-claim wiring: it claims each
 * `BUILDING` candidate's lease before building it, and a candidate whose claim fails - because
 * another node already holds an unexpired lease on it - is skipped rather than built twice.
 */
class AuditExportSchedulerTest
{
    private fun candidate(): AuditExport = AuditExport().apply {
        id = UUID.randomUUID()
        status = AuditExportStatus.BUILDING
    }

    private fun scheduler(
        exportRepo: AuditExportRepository,
        exportService: AuditExportService,
    ): AuditExportScheduler
    {
        val config = mock<AuditExportConfigService>()
        whenever(config.getBuildLeaseMinutes()).thenReturn(10)
        return AuditExportScheduler(exportRepo, exportService, config)
    }

    @Test
    fun `buildTick builds only the candidates it successfully claims`()
    {
        val claimed = candidate()
        val lostRace = candidate()
        val exportRepo = mock<AuditExportRepository>()
        whenever(exportRepo.findDueForBuilding(any(), any())).thenReturn(listOf(claimed, lostRace))
        val exportService = mock<AuditExportService>()
        whenever(exportService.claimForBuilding(eq(claimed.id), any(), any())).thenReturn(claimed)
        whenever(exportService.claimForBuilding(eq(lostRace.id), any(), any())).thenReturn(null)

        scheduler(exportRepo, exportService).buildTick()

        verify(exportService).processBuilding(claimed)
        verify(exportService, never()).processBuilding(lostRace)
    }

    @Test
    fun `buildTick claims every candidate under the same worker id`()
    {
        val first = candidate()
        val second = candidate()
        val exportRepo = mock<AuditExportRepository>()
        whenever(exportRepo.findDueForBuilding(any(), any())).thenReturn(listOf(first, second))
        val exportService = mock<AuditExportService>()
        whenever(exportService.claimForBuilding(any(), any(), any())).thenReturn(null)

        scheduler(exportRepo, exportService).buildTick()

        val workerIdCaptor = org.mockito.kotlin.argumentCaptor<String>()
        verify(exportService).claimForBuilding(eq(first.id), workerIdCaptor.capture(), any())
        val firstWorkerId = workerIdCaptor.firstValue
        verify(exportService).claimForBuilding(eq(second.id), eq(firstWorkerId), any())
        assertEquals(1, setOf(firstWorkerId).size)
    }

    @Test
    fun `repeated expiry ticks consume due exports only once`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val exportService = mock<AuditExportService>()
        whenever(exportService.expireDue(any())).thenReturn(2, 0)
        val scheduler = scheduler(exportRepo, exportService)

        scheduler.expireTick()
        scheduler.expireTick()

        verify(exportService, times(2)).expireDue(any())
    }
}
