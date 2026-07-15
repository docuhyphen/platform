package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.notification.UserNotificationPreference
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class ExchangeLifecycleNotificationService @Inject constructor(
    private val shareService: ShareService,
    private val inAppNotificationService: InAppNotificationService,
)
{
    fun publish(exchange: Exchange, status: ExchangeStatus)
    {
        val notification = when (status)
        {
            ExchangeStatus.ACCEPTED_STARTED -> NotificationDetails(
                UserNotificationPreference.EXCHANGE_ACCEPTED,
                "exchange.accepted",
                "Exchange accepted",
            )
            ExchangeStatus.REJECTED -> NotificationDetails(
                UserNotificationPreference.EXCHANGE_DECLINED,
                "exchange.declined",
                "Exchange declined",
            )
            ExchangeStatus.ENDED -> NotificationDetails(
                UserNotificationPreference.EXCHANGE_ENDED,
                "exchange.ended",
                "Exchange ended",
            )
            else -> return
        }

        val recipients = buildSet {
            exchange.initiator?.id?.let(::add)
            if (status == ExchangeStatus.ENDED)
            {
                addAll(shareService.recipientUserIdsForDisplay(exchange.id))
            }
        }
        val exchangeLabel = exchange.name.orEmpty().ifBlank { exchange.id.toString() }
        recipients.forEach { appUserId ->
            inAppNotificationService.publishIfEnabled(
                appUserId = appUserId,
                preference = notification.preference,
                type = notification.type,
                title = notification.title,
                message = "${notification.title}: $exchangeLabel.",
                data = mapOf("exchangeId" to exchange.id.toString()),
            )
        }
    }

    private data class NotificationDetails(
        val preference: UserNotificationPreference,
        val type: String,
        val title: String,
    )
}
