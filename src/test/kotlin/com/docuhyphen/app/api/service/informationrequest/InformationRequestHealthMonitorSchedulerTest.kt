package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestHealthIndicator
import com.docuhyphen.app.api.model.informationrequest.InformationRequestHealthIndicatorKey
import com.docuhyphen.app.api.model.informationrequest.InformationRequestHealthReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class InformationRequestHealthMonitorSchedulerTest
{
    private val healthService = mock<InformationRequestHealthService>()
    private val scheduler = InformationRequestHealthMonitorScheduler(healthService)

    @Test
    fun `a check reports exactly the breached indicators`()
    {
        val breached = InformationRequestHealthIndicator(InformationRequestHealthIndicatorKey.EVENT_DELIVERY_BACKLOG, 4, 0)
        whenever(healthService.report()).thenReturn(
            InformationRequestHealthReport(
                Instant.now(),
                listOf(breached, InformationRequestHealthIndicator(InformationRequestHealthIndicatorKey.RESERVATIONS_ABOVE_CAP, 0, 0)),
            ),
        )

        assertEquals(listOf(breached), scheduler.check())
    }

    @Test
    fun `a failing report never stops the scheduler`()
    {
        whenever(healthService.report()).thenThrow(IllegalStateException("database unavailable"))

        assertEquals(emptyList<InformationRequestHealthIndicator>(), scheduler.check())
    }
}
