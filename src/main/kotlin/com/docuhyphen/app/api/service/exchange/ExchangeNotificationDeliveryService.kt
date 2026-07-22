package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.realtime.RealtimeMessage
import com.docuhyphen.app.api.realtime.RealtimeMessageType
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.notification.UserNotificationPreference
import com.docuhyphen.app.api.service.notification.UserNotificationPreferenceService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import org.slf4j.LoggerFactory
import java.util.UUID

@ApplicationScoped
class ExchangeNotificationDeliveryService @Inject constructor(
    private val emailService: EmailService,
    private val inAppNotificationService: InAppNotificationService,
    private val preferenceService: UserNotificationPreferenceService,
    private val realtimeEventService: RealtimeEventService,
    private val transactionSynchronizationRegistry: TransactionSynchronizationRegistry,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeNotificationDeliveryService::class.java)
    }

    fun scheduleAfterCommit(
        exchangeId: UUID,
        exchangeStatus: String,
        emails: List<ExchangeEmailDelivery>,
        inAppNotifications: List<ExchangeInAppDelivery>,
        refreshAppUserIds: Set<UUID>,
    )
    {
        val emailSnapshot = emails.toList()
        val inAppSnapshot = inAppNotifications.toList()
        val refreshSnapshot = refreshAppUserIds.toSet()
        transactionSynchronizationRegistry.registerInterposedSynchronization(
            object : Synchronization
            {
                override fun beforeCompletion() = Unit

                override fun afterCompletion(status: Int)
                {
                    if (status != Status.STATUS_COMMITTED)
                    {
                        return
                    }
                    deliverEmails(emailSnapshot)
                    deliverInApp(inAppSnapshot)
                    broadcastRefresh(exchangeId, exchangeStatus, refreshSnapshot)
                }
            },
        )
    }

    private fun deliverEmails(deliveries: List<ExchangeEmailDelivery>)
    {
        deliveries.forEach { delivery ->
            try
            {
                if (delivery.preferenceAppUserId != null &&
                    !preferenceService.isEnabled(
                        delivery.preferenceAppUserId,
                        UserNotificationPreference.EXCHANGE_INITIATED,
                        NotificationChannelType.EMAIL,
                    ))
                {
                    return@forEach
                }
                emailService.sendEmail(
                    to = delivery.to,
                    subject = delivery.subject,
                    body = delivery.body,
                    useHtml = true,
                )
            }
            catch (e: Exception)
            {
                logger.error("Failed to deliver committed Exchange email to {}", delivery.to, e)
            }
        }
    }

    private fun deliverInApp(deliveries: List<ExchangeInAppDelivery>)
    {
        deliveries.forEach { delivery ->
            try
            {
                inAppNotificationService.publishIfEnabled(
                    appUserId = delivery.appUserId,
                    preference = UserNotificationPreference.EXCHANGE_INITIATED,
                    type = delivery.type,
                    title = delivery.title,
                    message = delivery.message,
                    data = delivery.data,
                )
            }
            catch (e: Exception)
            {
                logger.error(
                    "Failed to deliver committed Exchange notification to app user {}",
                    delivery.appUserId,
                    e,
                )
            }
        }
    }

    private fun broadcastRefresh(exchangeId: UUID, exchangeStatus: String, appUserIds: Set<UUID>)
    {
        val message = RealtimeMessage(
            type = RealtimeMessageType.EXCHANGE_LIST_CHANGED,
            exchangeId = exchangeId.toString(),
            status = exchangeStatus,
        )
        appUserIds.forEach { appUserId ->
            try
            {
                realtimeEventService.broadcastToUser(appUserId, message)
            }
            catch (e: Exception)
            {
                logger.error("Failed to refresh Exchange requests for app user {}", appUserId, e)
            }
        }
    }
}
