package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.notification.DomainEventConsumptionResult
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventConsumer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestNoticeConsumer @Inject constructor(
    private val worker: InformationRequestNoticeWorker,
) : DomainEventConsumer
{
    override val consumerKey: String = CONSUMER_KEY

    override fun handles(event: DomainEvent): Boolean =
        event.subject?.type == SUBJECT_TYPE &&
            NOTICE_COUNT_KEYS.any { key -> (event.payload[key]?.toIntOrNull() ?: 0) > 0 }

    override fun consume(event: DomainEvent): DomainEventConsumptionResult
    {
        val result = worker.dispatchForRequest(UUID.fromString(requireNotNull(event.subject).id))
        return DomainEventConsumptionResult.applied("rendered=${result.rendered} delivered=${result.delivered} failed=${result.failed}")
    }

    companion object
    {
        const val CONSUMER_KEY = "information-request-notice-dispatch"
        private const val SUBJECT_TYPE = "INFORMATION_REQUEST"
        private val NOTICE_COUNT_KEYS = listOf("noticeIntentCount", "pendingNoticeCount")
    }
}
