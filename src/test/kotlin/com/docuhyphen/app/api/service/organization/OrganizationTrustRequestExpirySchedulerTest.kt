package com.docuhyphen.app.api.service.organization

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.mock

class OrganizationTrustRequestExpirySchedulerTest
{
    @Test
    fun `repeated ticks remain safe after due requests are consumed`()
    {
        val service = mock<OrganizationTrustRelationshipService>()
        val notificationService = mock<OrganizationTrustNotificationService>()
        whenever(service.expireDueRequestRecords(any(), eq(100))).thenReturn(
            List(3) { mock() },
            emptyList(),
        )
        val scheduler = OrganizationTrustRequestExpiryScheduler(service, notificationService)

        scheduler.expireTick()
        scheduler.expireTick()

        verify(service, times(2)).expireDueRequestRecords(any(), eq(100))
        verify(notificationService, times(3)).publish(any(), any())
    }
}
