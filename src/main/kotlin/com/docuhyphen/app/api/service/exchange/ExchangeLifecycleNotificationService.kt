package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.exchange.ExchangeLifecycleNotificationDetails
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.notification.UserNotificationPreference
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

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
            ExchangeStatus.ACCEPTED_STARTED -> ExchangeLifecycleNotificationDetails(
                UserNotificationPreference.EXCHANGE_ACCEPTED,
                "exchange.accepted",
                "Exchange accepted",
            )

            ExchangeStatus.REJECTED -> ExchangeLifecycleNotificationDetails(
                UserNotificationPreference.EXCHANGE_DECLINED,
                "exchange.declined",
                "Exchange declined",
            )

            ExchangeStatus.ENDED -> ExchangeLifecycleNotificationDetails(
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

    fun publishEndingRefused(exchange: Exchange, recipientUserIds: Set<UUID>) {
        val exchangeLabel = exchange.name.orEmpty().ifBlank { exchange.id.toString() }
        recipientUserIds.forEach { appUserId ->
            inAppNotificationService.publishIfEnabled(
                appUserId = appUserId,
                preference = UserNotificationPreference.EXCHANGE_ENDED,
                type = ENDING_REFUSED_TYPE,
                title = ENDING_REFUSED_TITLE,
                message = "$ENDING_REFUSED_TITLE: $exchangeLabel. Finish or cancel its open Information Requests, then end it again.",
                data = mapOf("exchangeId" to exchange.id.toString()),
            )
        }
    }

    private companion object {
        const val ENDING_REFUSED_TYPE = "exchange.ending_refused"
        const val ENDING_REFUSED_TITLE = "Exchange not ended"
    }
}
