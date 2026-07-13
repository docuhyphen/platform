package com.docuhyphen.app.api.service.audit

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AuditEngagementSchedulerTest
{
    @Test
    fun `repeated ticks remain safe when the first tick consumes all due engagements`()
    {
        val service = mock<AuditEngagementService>()
        whenever(service.expireDue(any())).thenReturn(2, 0)
        val scheduler = AuditEngagementScheduler(service)

        scheduler.expireTick()
        scheduler.expireTick()

        verify(service, times(2)).expireDue(any())
    }
}
