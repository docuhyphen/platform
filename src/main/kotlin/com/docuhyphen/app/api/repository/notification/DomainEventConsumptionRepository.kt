package com.docuhyphen.app.api.repository.notification

import com.docuhyphen.app.api.model.notification.DomainEventConsumptionOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.util.*

@ApplicationScoped
class DomainEventConsumptionRepository @Inject constructor(
    private val entityManager: EntityManager,
)
{
    fun outcomeOf(consumerKey: String, eventId: UUID): DomainEventConsumptionOutcome? =
        entityManager.createNativeQuery(
            "SELECT outcome FROM domain_event_consumption WHERE consumer_key = :consumer AND event_id = :event",
        )
            .setParameter("consumer", consumerKey)
            .setParameter("event", eventId)
            .resultList
            .firstOrNull()
            ?.let { DomainEventConsumptionOutcome.valueOf(it.toString()) }

    fun record(consumerKey: String, eventId: UUID, outcome: DomainEventConsumptionOutcome, detail: String?)
    {
        entityManager.createNativeQuery(
            """
            INSERT INTO domain_event_consumption (consumer_key, event_id, outcome, detail, consumed_at)
            VALUES (:consumer, :event, :outcome, :detail, CURRENT_TIMESTAMP)
            """.trimIndent(),
        )
            .setParameter("consumer", consumerKey)
            .setParameter("event", eventId)
            .setParameter("outcome", outcome.name)
            .setParameter("detail", detail?.take(DETAIL_LENGTH))
            .executeUpdate()
    }

    fun skippedCounts(orderingKeys: Collection<String>): Map<String, Int>
    {
        if (orderingKeys.isEmpty()) return emptyMap()
        return entityManager.createNativeQuery(
            """
            SELECT outbox.ordering_key, COUNT(*)
            FROM domain_event_consumption consumption
            JOIN workflow_event_outbox outbox ON outbox.event_id = consumption.event_id
            WHERE outbox.ordering_key IN (:keys)
              AND consumption.outcome = 'SKIPPED'
            GROUP BY outbox.ordering_key
            """.trimIndent(),
        )
            .setParameter("keys", orderingKeys)
            .resultList
            .associate { row -> (row as Array<*>)[0] as String to (row[1] as Number).toInt() }
    }

    private companion object
    {
        const val DETAIL_LENGTH = 512
    }
}
