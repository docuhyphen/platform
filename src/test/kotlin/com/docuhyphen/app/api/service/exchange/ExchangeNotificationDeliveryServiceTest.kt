package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.notification.UserNotificationPreference
import com.docuhyphen.app.api.service.notification.UserNotificationPreferenceService
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class ExchangeNotificationDeliveryServiceTest
{
    private val emailService = mock<EmailService>()
    private val inAppNotificationService = mock<InAppNotificationService>()
    private val preferenceService = mock<UserNotificationPreferenceService>()
    private val realtimeEventService = mock<RealtimeEventService>()
    private val registry = mock<TransactionSynchronizationRegistry>()
    private val service = ExchangeNotificationDeliveryService(
        emailService,
        inAppNotificationService,
        preferenceService,
        realtimeEventService,
        registry,
    )

    @Test
    fun `rolled back mutation produces no notification delivery`()
    {
        val appUserId = UUID.randomUUID()
        service.scheduleAfterCommit(
            exchangeId = UUID.randomUUID(),
            exchangeStatus = "INITIATED",
            emails = listOf(ExchangeEmailDelivery("recipient@example.test", "Subject", "Body", appUserId)),
            inAppNotifications = listOf(
                ExchangeInAppDelivery(
                    appUserId,
                    "exchange.recipient_invitation",
                    "Invitation",
                    "Message",
                    emptyMap(),
                ),
            ),
            refreshAppUserIds = setOf(appUserId),
        )

        val synchronization = registeredSynchronization()
        synchronization.afterCompletion(Status.STATUS_ROLLEDBACK)

        verifyNoInteractions(emailService, inAppNotificationService, preferenceService, realtimeEventService)
    }

    @Test
    fun `successful commit delivers each configured channel once`()
    {
        val exchangeId = UUID.randomUUID()
        val appUserId = UUID.randomUUID()
        whenever(
            preferenceService.isEnabled(
                appUserId,
                UserNotificationPreference.EXCHANGE_INITIATED,
                NotificationChannelType.EMAIL,
            ),
        ).thenReturn(true)
        service.scheduleAfterCommit(
            exchangeId = exchangeId,
            exchangeStatus = "INITIATED",
            emails = listOf(ExchangeEmailDelivery("recipient@example.test", "Subject", "Body", appUserId)),
            inAppNotifications = listOf(
                ExchangeInAppDelivery(
                    appUserId,
                    "exchange.recipient_invitation",
                    "Invitation",
                    "Message",
                    mapOf("exchangeId" to exchangeId.toString()),
                ),
            ),
            refreshAppUserIds = setOf(appUserId),
        )

        registeredSynchronization().afterCompletion(Status.STATUS_COMMITTED)

        verify(emailService).sendEmail("recipient@example.test", "Subject", "Body", true)
        verify(inAppNotificationService).publishIfEnabled(
            appUserId,
            UserNotificationPreference.EXCHANGE_INITIATED,
            "exchange.recipient_invitation",
            "Invitation",
            "Message",
            mapOf("exchangeId" to exchangeId.toString()),
        )
        verify(realtimeEventService).broadcastToUser(eq(appUserId), any(), isNull())
    }

    @Test
    fun `disabled email preference does not suppress in app or realtime delivery`()
    {
        val appUserId = UUID.randomUUID()
        whenever(
            preferenceService.isEnabled(
                appUserId,
                UserNotificationPreference.EXCHANGE_INITIATED,
                NotificationChannelType.EMAIL,
            ),
        ).thenReturn(false)
        service.scheduleAfterCommit(
            exchangeId = UUID.randomUUID(),
            exchangeStatus = "INITIATED",
            emails = listOf(ExchangeEmailDelivery("recipient@example.test", "Subject", "Body", appUserId)),
            inAppNotifications = listOf(
                ExchangeInAppDelivery(appUserId, "exchange.initiated", "Title", "Message", emptyMap()),
            ),
            refreshAppUserIds = setOf(appUserId),
        )

        registeredSynchronization().afterCompletion(Status.STATUS_COMMITTED)

        verify(emailService, never()).sendEmail(any(), any(), any(), any())
        verify(inAppNotificationService).publishIfEnabled(any(), any(), any(), any(), any(), any())
        verify(realtimeEventService).broadcastToUser(eq(appUserId), any(), isNull())
    }

    private fun registeredSynchronization(): Synchronization
    {
        val captor = argumentCaptor<Synchronization>()
        verify(registry).registerInterposedSynchronization(captor.capture())
        return captor.firstValue
    }
}
