package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.InAppNotificationMapper
import com.docuhyphen.app.api.model.dto.NotificationDto
import com.docuhyphen.app.api.model.entity.InAppNotification
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.notification.InAppNotificationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.util.UUID

class InAppNotificationServicePaginationTest
{
    private val repository = mock<InAppNotificationRepository>()
    private val mapper = mock<InAppNotificationMapper>()
    private val preferenceService = mock<UserNotificationPreferenceService>()
    private val realtimeEventService = mock<RealtimeEventService>()
    private val service = InAppNotificationService(
        repository,
        mapper,
        preferenceService,
        realtimeEventService,
    )

    @Test
    fun `returns a cursor when another notification page exists`()
    {
        val appUserId = UUID.randomUUID()
        val notifications = listOf(
            notification(3000),
            notification(2000),
            notification(1000),
        )
        whenever(repository.findPageForUser(appUserId, 3, null, null)).thenReturn(notifications)
        whenever(repository.countUnread(appUserId)).thenReturn(7)
        whenever(mapper.toDto(any())).thenAnswer { invocation ->
            val item = invocation.getArgument<InAppNotification>(0)
            NotificationDto(
                id = item.id.toString(),
                type = item.eventType,
                message = item.body.orEmpty(),
                timestamp = item.createdAt,
            )
        }

        val page = service.listPage(appUserId, 2, null, null)

        assertEquals(2, page.notifications.size)
        assertTrue(page.hasMore)
        assertEquals(7, page.unreadCount)
        assertEquals(notifications[1].id.toString(), page.nextCursor?.id)
        assertEquals(2000, page.nextCursor?.timestamp)
    }

    private fun notification(timestamp: Long): InAppNotification = InAppNotification().apply {
        appUserId = UUID.randomUUID()
        eventType = "exchange.accepted"
        title = "Exchange accepted"
        body = "Exchange accepted"
        createdAt = Timestamp(timestamp)
    }
}
