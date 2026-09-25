package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestEvidenceScanSchedulerTest
{
    private val assessmentService: InformationRequestEvidenceMalwareAssessmentService = mock()
    private val scheduler = InformationRequestEvidenceScanScheduler(assessmentService)

    @Test
    fun `with no scanner configured nothing is selected or recorded`()
    {
        whenever(assessmentService.scannerConfigured()).thenReturn(false)

        scheduler.tick()

        verify(assessmentService, never()).dueForScan()
        verify(assessmentService, never()).assess(any())
    }

    @Test
    fun `every version due for a scan is assessed and one failure never stops the rest`()
    {
        val first = UUID.randomUUID()
        val failing = UUID.randomUUID()
        val last = UUID.randomUUID()
        whenever(assessmentService.scannerConfigured()).thenReturn(true)
        whenever(assessmentService.dueForScan()).thenReturn(listOf(first, failing, last))
        whenever(assessmentService.assess(first)).thenReturn(InformationRequestEvidenceAssessment())
        whenever(assessmentService.assess(failing)).thenThrow(IllegalStateException("storage unavailable"))
        whenever(assessmentService.assess(last)).thenReturn(InformationRequestEvidenceAssessment())

        scheduler.tick()

        verify(assessmentService).assess(first)
        verify(assessmentService).assess(failing)
        verify(assessmentService).assess(last)
    }
}
