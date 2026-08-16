package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.InAppNotificationMapper
import com.docuhyphen.app.api.model.entity.InAppNotification
import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.notification.InAppNotificationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.util.UUID

class InAppNotificationServiceTest
{
    @Test
    fun `persists and pushes an enabled in-app notification with navigation metadata`()
    {
        val appUserId = UUID.randomUUID()
        val repository = mock<InAppNotificationRepository>()
        val preferenceService = mock<UserNotificationPreferenceService>()
        val realtimeEventService = mock<RealtimeEventService>()
        whenever(
            preferenceService.isEnabled(
                appUserId,
                UserNotificationPreference.DOCUMENT_UPLOADED,
                NotificationChannelType.IN_APP,
            ),
        ).thenReturn(true)
        whenever(repository.save(any<InAppNotification>())).thenAnswer { it.arguments[0] }
        val service = InAppNotificationService(
            repository,
            InAppNotificationMapper(),
            preferenceService,
            realtimeEventService,
        )

        val result = service.publishIfEnabled(
            appUserId = appUserId,
            preference = UserNotificationPreference.DOCUMENT_UPLOADED,
            type = "document.uploaded",
            title = "Document uploaded",
            message = "A document was uploaded.",
            data = mapOf("exchangeId" to "exchange-1", "documentId" to "document-1"),
        )

        assertNotNull(result)
        assertEquals("exchange-1", result?.exchangeId)
        assertEquals("document-1", result?.documentId)
        val notificationCaptor = argumentCaptor<com.docuhyphen.app.api.model.dto.NotificationDto>()
        verify(realtimeEventService).broadcastNotificationToUser(
            eq(appUserId),
            notificationCaptor.capture(),
        )
        assertEquals(result, notificationCaptor.firstValue)
    }

    @Test
    fun `marks unread notifications that match event type and payload data`()
    {
        val appUserId = UUID.randomUUID()
        val matching = unreadNotification(
            eventType = "document.commented",
            payloadJson = """{"exchangeId":"exchange-1","documentId":"document-1"}""",
        )
        val otherDocument = unreadNotification(
            eventType = "document.commented",
            payloadJson = """{"exchangeId":"exchange-1","documentId":"document-2"}""",
        )
        val repository = mock<InAppNotificationRepository>()
        val preferenceService = mock<UserNotificationPreferenceService>()
        val realtimeEventService = mock<RealtimeEventService>()
        whenever(
            repository.findUnreadByEventTypesForUser(
                appUserId,
                setOf("document.commented"),
            ),
        ).thenReturn(listOf(matching, otherDocument))
        whenever(repository.countUnread(appUserId)).thenReturn(1)
        val service = InAppNotificationService(
            repository,
            InAppNotificationMapper(),
            preferenceService,
            realtimeEventService,
        )

        val result = service.markAsRead(
            appUserId = appUserId,
            criteria = NotificationReadCriteria(
                eventTypes = setOf("document.commented"),
                data = mapOf("exchangeId" to "exchange-1", "documentId" to "document-1"),
            ),
        )

        assertEquals(listOf(matching.id), result.readNotificationIds)
        assertEquals(1, result.unreadCount)
        assertTrue(matching.isRead)
        assertNotNull(matching.readAt)
        assertFalse(otherDocument.isRead)
        verify(repository).update(matching)
    }

    private fun unreadNotification(eventType: String, payloadJson: String): InAppNotification =
        InAppNotification().apply {
            appUserId = UUID.randomUUID()
            this.eventType = eventType
            title = "Notification"
            body = "Notification"
            this.payloadJson = payloadJson
            createdAt = Timestamp(1000)
            isRead = false
        }
}
