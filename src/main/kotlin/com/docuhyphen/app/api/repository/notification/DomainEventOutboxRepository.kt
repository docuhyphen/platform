package com.docuhyphen.app.api.repository.notification

import com.docuhyphen.app.api.model.entity.DomainEventOutboxEntry
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.NoResultException
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class DomainEventOutboxRepository :
    BaseRepository<DomainEventOutboxEntry>(DomainEventOutboxEntry::class.java)
{
    fun findByEventId(eventId: UUID): DomainEventOutboxEntry? =
        try
        {
            entityManager.createQuery(
                "SELECT e FROM DomainEventOutboxEntry e WHERE e.eventId = :eid",
                DomainEventOutboxEntry::class.java,
            )
                .setParameter("eid", eventId)
                .singleResult
        }
        catch (e: NoResultException)
        {
            null
        }

    fun insert(entry: DomainEventOutboxEntry): DomainEventOutboxEntry = save(entry)

    fun claimNextPending(now: Timestamp): DomainEventOutboxEntry?
    {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """SELECT * FROM workflow_event_outbox candidate
               WHERE candidate.status = 'PENDING' AND candidate.next_attempt_at <= :now
                 AND (candidate.ordering_key IS NULL OR NOT EXISTS (
                     SELECT 1 FROM workflow_event_outbox earlier
                     WHERE earlier.ordering_key = candidate.ordering_key
                       AND earlier.status = 'PENDING'
                       AND earlier.sequence_number < candidate.sequence_number))
               ORDER BY candidate.next_attempt_at ASC, candidate.sequence_number ASC
               LIMIT 1
               FOR UPDATE OF candidate SKIP LOCKED""",
            DomainEventOutboxEntry::class.java,
        )
            .setParameter("now", now)
            .resultList as List<DomainEventOutboxEntry>
        return rows.firstOrNull()
    }

    fun countByStatus(status: String): Long =
        entityManager.createQuery(
            "SELECT COUNT(e) FROM DomainEventOutboxEntry e WHERE e.status = :status",
            Long::class.javaObjectType,
        )
            .setParameter("status", status)
            .singleResult

    fun oldestPendingCreatedAt(): Timestamp? =
        entityManager.createQuery(
            "SELECT MIN(e.createdAt) FROM DomainEventOutboxEntry e WHERE e.status = :status",
            Timestamp::class.java,
        )
            .setParameter("status", DomainEventOutboxEntry.STATUS_PENDING)
            .resultList
            .firstOrNull()

    fun countPendingWithAttemptsAtLeast(minAttempts: Int): Long =
        entityManager.createQuery(
            """SELECT COUNT(e) FROM DomainEventOutboxEntry e
               WHERE e.status = :status AND e.attemptCount >= :minAttempts""",
            Long::class.javaObjectType,
        )
            .setParameter("status", DomainEventOutboxEntry.STATUS_PENDING)
            .setParameter("minAttempts", minAttempts)
            .singleResult

    fun failingDeliveryCounts(orderingKeys: Collection<String>): Map<String, Int>
    {
        if (orderingKeys.isEmpty()) return emptyMap()
        return entityManager.createQuery(
            """
            SELECT e.orderingKey, COUNT(e)
            FROM DomainEventOutboxEntry e
            WHERE e.orderingKey IN :keys
              AND ((e.status = :pending AND e.attemptCount > 0) OR e.status = :failed)
            GROUP BY e.orderingKey
            """.trimIndent(),
            Array<Any>::class.java,
        )
            .setParameter("keys", orderingKeys)
            .setParameter("pending", DomainEventOutboxEntry.STATUS_PENDING)
            .setParameter("failed", DomainEventOutboxEntry.STATUS_FAILED)
            .resultList
            .associate { row -> row[0] as String to (row[1] as Number).toInt() }
    }
}
