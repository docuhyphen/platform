package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.exchange.ExchangeEmailDelivery
import com.docuhyphen.app.api.service.exchange.ExchangeInAppDelivery
import com.docuhyphen.app.api.service.exchange.ExchangeNotificationDeliveryService
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.notification.UserNotificationPreference
import com.docuhyphen.app.api.service.notification.UserNotificationPreferenceService
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

internal class ExchangeNotificationPermutationFixture
{
    val exchangeId: UUID = UUID.randomUUID()
    val appUserId: UUID = UUID.randomUUID()
    val emailService: EmailService = mock()
    val inAppService: InAppNotificationService = mock()
    val preferenceService: UserNotificationPreferenceService = mock()
    val realtimeService: RealtimeEventService = mock()
    private val registry: TransactionSynchronizationRegistry = mock()
    private val service = ExchangeNotificationDeliveryService(
        emailService,
        inAppService,
        preferenceService,
        realtimeService,
        registry,
    )

    init
    {
        whenever(
            preferenceService.isEnabled(
                appUserId,
                UserNotificationPreference.EXCHANGE_INITIATED,
                NotificationChannelType.EMAIL,
            ),
        ).thenReturn(true)
    }

    fun schedule(
        emailBody: String = "Body",
        inAppType: String = "exchange.recipient_invitation",
    )
    {
        service.scheduleAfterCommit(
            exchangeId = exchangeId,
            exchangeStatus = "INITIATED",
            emails = listOf(
                ExchangeEmailDelivery(
                    to = "recipient@example.test",
                    subject = "Invitation",
                    body = emailBody,
                    preferenceAppUserId = appUserId,
                ),
            ),
            inAppNotifications = listOf(
                ExchangeInAppDelivery(
                    appUserId = appUserId,
                    type = inAppType,
                    title = "Invitation",
                    message = "Message",
                    data = mapOf("exchangeId" to exchangeId.toString()),
                ),
            ),
            refreshAppUserIds = setOf(appUserId),
        )
    }

    fun complete(committed: Boolean)
    {
        val captor = argumentCaptor<Synchronization>()
        verify(registry).registerInterposedSynchronization(captor.capture())
        captor.firstValue.afterCompletion(
            if (committed) Status.STATUS_COMMITTED else Status.STATUS_ROLLEDBACK,
        )
    }
}
