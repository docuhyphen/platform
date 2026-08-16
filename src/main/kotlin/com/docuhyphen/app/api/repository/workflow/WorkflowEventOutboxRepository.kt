package com.docuhyphen.app.api.repository.workflow

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.WorkflowEventOutboxEntry
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.NoResultException
import java.sql.Timestamp
import java.util.UUID

@ApplicationScoped
class WorkflowEventOutboxRepository :
    BaseRepository<WorkflowEventOutboxEntry>(WorkflowEventOutboxEntry::class.java)
{
    /**
     * Looks up an existing row by its logical event id. The publisher uses this before inserting so
     * a retried enqueue for the same occurrence does not write a duplicate; the `event_id` unique
     * constraint is the authoritative belt-and-braces guard.
     */
    fun findByEventId(eventId: UUID): WorkflowEventOutboxEntry? =
        try
        {
            entityManager.createQuery(
                "SELECT e FROM WorkflowEventOutboxEntry e WHERE e.eventId = :eid",
                WorkflowEventOutboxEntry::class.java,
            )
                .setParameter("eid", eventId)
                .singleResult
        }
        catch (e: NoResultException)
        {
            null
        }

    /**
     * Inserts [entry] in the caller's active transaction (no `REQUIRES_NEW`) so it commits or rolls
     * back atomically with the workflow state mutation that produced it.
     */
    fun insert(entry: WorkflowEventOutboxEntry): WorkflowEventOutboxEntry = save(entry)

    /**
     * Claims the single oldest deliverable pending row (`next_attempt_at <= now`) under a row-level
     * write lock, skipping rows already locked by another dispatcher transaction (`FOR UPDATE SKIP
     * LOCKED`). This makes the dispatcher safe to run on multiple application replicas: two nodes
     * never claim the same row, and the lock is held for the whole delivery so no row is delivered
     * twice concurrently. Returns null when nothing is currently deliverable.
     */
    fun claimNextPending(now: Timestamp): WorkflowEventOutboxEntry?
    {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """SELECT * FROM workflow_event_outbox
               WHERE status = 'PENDING' AND next_attempt_at <= :now
               ORDER BY next_attempt_at ASC
               LIMIT 1
               FOR UPDATE SKIP LOCKED""",
            WorkflowEventOutboxEntry::class.java,
        )
            .setParameter("now", now)
            .resultList as List<WorkflowEventOutboxEntry>
        return rows.firstOrNull()
    }

    /** Count of rows in [status]. Used for backlog health reporting. */
    fun countByStatus(status: String): Long =
        entityManager.createQuery(
            "SELECT COUNT(e) FROM WorkflowEventOutboxEntry e WHERE e.status = :status",
            Long::class.javaObjectType,
        )
            .setParameter("status", status)
            .singleResult

    /** Creation time of the oldest pending row, or null when nothing is pending. */
    fun oldestPendingCreatedAt(): Timestamp? =
        entityManager.createQuery(
            "SELECT MIN(e.createdAt) FROM WorkflowEventOutboxEntry e WHERE e.status = :status",
            Timestamp::class.java,
        )
            .setParameter("status", WorkflowEventOutboxEntry.STATUS_PENDING)
            .resultList
            .firstOrNull()

    /** Count of pending rows that have already failed at least [minAttempts] delivery attempts. */
    fun countPendingWithAttemptsAtLeast(minAttempts: Int): Long =
        entityManager.createQuery(
            """SELECT COUNT(e) FROM WorkflowEventOutboxEntry e
               WHERE e.status = :status AND e.attemptCount >= :minAttempts""",
            Long::class.javaObjectType,
        )
            .setParameter("status", WorkflowEventOutboxEntry.STATUS_PENDING)
            .setParameter("minAttempts", minAttempts)
            .singleResult
}
