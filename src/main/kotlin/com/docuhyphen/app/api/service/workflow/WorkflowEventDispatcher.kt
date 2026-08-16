package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowEventOutboxEntry
import com.docuhyphen.app.api.repository.workflow.WorkflowEventOutboxRepository
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventJson
import com.docuhyphen.app.api.service.notification.EventRouter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

/** Outcome of a [WorkflowEventDispatcher.dispatch] pass. */
data class DispatchResult(val delivered: Int, val retried: Int, val failed: Int)

/**
 * Point-in-time backlog health for the workflow event outbox. Reported for operational visibility;
 * never carries event payload contents.
 */
data class OutboxBacklogHealth(
    val pending: Long,
    val oldestPendingAgeSeconds: Long,
    val repeatedFailures: Long,
    val exhausted: Long,
)

/**
 * Delivers committed `workflow_event_outbox` rows after commit. Each row is claimed under a
 * `FOR UPDATE SKIP LOCKED` row lock ([WorkflowEventOutboxRepository.claimNextPending]) and routed in
 * its own transaction, so the dispatcher is safe to run concurrently on multiple application
 * replicas: two nodes never deliver the same row.
 *
 * Delivery semantics:
 *   * On success the row is marked DELIVERED.
 *   * A routing failure (a required lifecycle side-effect that did not apply, or a transient error)
 *     leaves the row PENDING, increments the attempt count, records a safe error summary, and
 *     schedules the next attempt with bounded exponential backoff. Required events are never
 *     discarded: after the attempt threshold the row keeps retrying at the capped interval and is
 *     surfaced through backlog health rather than dropped.
 *   * Only a non-retryable envelope decode error marks the row FAILED, since retrying cannot help.
 *
 * All logic lives here (not in the scheduler) so it can be driven synchronously from tests or an
 * admin action.
 */
@ApplicationScoped
class WorkflowEventDispatcher
{
    private val logger = LoggerFactory.getLogger(WorkflowEventDispatcher::class.java)

    @Inject private lateinit var outboxRepository: WorkflowEventOutboxRepository
    @Inject private lateinit var eventRouter: EventRouter

    /**
     * Contextual reference to this bean's CDI proxy. Per-row delivery must cross the proxy so the
     * `@Transactional` boundary on [deliverNext] actually applies (a direct `this.deliverNext(...)`
     * self-invocation would bypass the interceptor). Falls back to `this` when running outside CDI,
     * such as in a plain unit test, where transactions are a no-op anyway.
     */
    @Inject private lateinit var self: WorkflowEventDispatcher

    private val json = DomainEventJson.instance

    private val transactionalSelf: WorkflowEventDispatcher
        get() = if (::self.isInitialized) self else this

    /**
     * Claims and delivers up to [maxRows] deliverable rows, one transaction per row. Stops early
     * when no row is currently deliverable.
     */
    fun dispatch(maxRows: Int = DEFAULT_BATCH_SIZE): DispatchResult
    {
        var delivered = 0
        var retried = 0
        var failed = 0

        repeat(maxRows.coerceIn(1, MAX_BATCH_SIZE)) {
            when (transactionalSelf.deliverNext(Timestamp.from(Instant.now())))
            {
                DeliveryOutcome.NONE -> return DispatchResult(delivered, retried, failed)
                DeliveryOutcome.DELIVERED -> delivered++
                DeliveryOutcome.RETRIED -> retried++
                DeliveryOutcome.FAILED -> failed++
            }
        }

        return DispatchResult(delivered, retried, failed)
    }

    /**
     * Claims the oldest deliverable row and routes it in a single transaction so the row lock is
     * held for the whole delivery. Returns [DeliveryOutcome.NONE] when nothing is deliverable.
     */
    @Transactional
    open fun deliverNext(now: Timestamp): DeliveryOutcome
    {
        val entry = outboxRepository.claimNextPending(now) ?: return DeliveryOutcome.NONE

        val event = runCatching { json.decodeFromString(DomainEvent.serializer(), entry.envelopeJson) }
            .getOrElse { e ->
                // A corrupt envelope cannot be delivered by retrying; mark it terminally failed so it
                // stops occupying the deliverable queue, and surface it for operator inspection.
                entry.status = WorkflowEventOutboxEntry.STATUS_FAILED
                entry.attemptCount += 1
                entry.lastError = safeError("envelope decode failed", e)
                outboxRepository.update(entry)
                logger.error("Workflow outbox row id={} type={} has an undecodable envelope; marked FAILED", entry.id, entry.eventType)
                return DeliveryOutcome.FAILED
            }

        return try
        {
            eventRouter.routeDurable(event)
            entry.status = WorkflowEventOutboxEntry.STATUS_DELIVERED
            entry.deliveredAt = now
            entry.lastError = null
            outboxRepository.update(entry)
            DeliveryOutcome.DELIVERED
        }
        catch (t: Throwable)
        {
            entry.attemptCount += 1
            entry.lastError = safeError("routing failed", t)
            entry.nextAttemptAt = Timestamp.from(now.toInstant().plus(backoff(entry.attemptCount)))
            outboxRepository.update(entry)
            logger.warn(
                "Workflow outbox delivery failed for id={} type={} attempt={}; will retry",
                entry.id, entry.eventType, entry.attemptCount,
            )
            DeliveryOutcome.RETRIED
        }
    }

    /** Point-in-time backlog health snapshot for operational monitoring. */
    fun backlogHealth(): OutboxBacklogHealth
    {
        val pending = outboxRepository.countByStatus(WorkflowEventOutboxEntry.STATUS_PENDING)
        val oldest = outboxRepository.oldestPendingCreatedAt()
        val oldestAgeSeconds = oldest?.let {
            Duration.between(it.toInstant(), Instant.now()).seconds.coerceAtLeast(0)
        } ?: 0L
        val repeatedFailures = outboxRepository.countPendingWithAttemptsAtLeast(REPEATED_FAILURE_THRESHOLD)
        val exhausted = outboxRepository.countPendingWithAttemptsAtLeast(EXHAUSTED_THRESHOLD)
        return OutboxBacklogHealth(pending, oldestAgeSeconds, repeatedFailures, exhausted)
    }

    /**
     * Bounded exponential backoff: `base * 2^(attempt-1)`, capped at [MAX_BACKOFF]. A required event
     * that keeps failing therefore retries at most once per [MAX_BACKOFF] indefinitely rather than
     * being discarded.
     */
    private fun backoff(attempt: Int): Duration
    {
        val exponent = (attempt - 1).coerceIn(0, 30)
        val millis = BASE_BACKOFF.toMillis() shl exponent
        val capped = if (millis <= 0L || millis > MAX_BACKOFF.toMillis()) MAX_BACKOFF.toMillis() else millis
        return Duration.ofMillis(capped)
    }

    /** Compact, non-sensitive error summary: exception type and message only, length-bounded. */
    private fun safeError(context: String, t: Throwable): String
    {
        val detail = t.message?.take(256) ?: t.javaClass.simpleName
        return "$context: ${t.javaClass.simpleName}: $detail".take(1024)
    }

    enum class DeliveryOutcome { NONE, DELIVERED, RETRIED, FAILED }

    companion object
    {
        const val DEFAULT_BATCH_SIZE = 100
        const val MAX_BATCH_SIZE = 1000

        /** Pending rows at or above this attempt count are reported as repeatedly failing. */
        const val REPEATED_FAILURE_THRESHOLD = 3

        /** Pending rows at or above this attempt count are reported as attempt-exhausted (still retried). */
        const val EXHAUSTED_THRESHOLD = 10

        private val BASE_BACKOFF: Duration = Duration.ofSeconds(30)
        private val MAX_BACKOFF: Duration = Duration.ofHours(1)
    }
}
