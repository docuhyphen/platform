package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.DomainEventOutboxEntry
import com.docuhyphen.app.api.model.notification.DispatchResult
import com.docuhyphen.app.api.model.notification.DomainEvent
import com.docuhyphen.app.api.model.notification.DomainEventJson
import com.docuhyphen.app.api.model.notification.OutboxBacklogHealth
import com.docuhyphen.app.api.repository.notification.DomainEventOutboxRepository
import com.docuhyphen.app.api.service.notification.DomainEventDispatcher.Companion.MAX_BACKOFF
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

@ApplicationScoped
class DomainEventDispatcher
{
    private val logger = LoggerFactory.getLogger(DomainEventDispatcher::class.java)

    @Inject
    private lateinit var outboxRepository: DomainEventOutboxRepository
    @Inject
    private lateinit var eventRouter: EventRouter

    @Inject
    private lateinit var self: DomainEventDispatcher

    private val json = DomainEventJson.instance

    private val transactionalSelf: DomainEventDispatcher
        get() = if (::self.isInitialized) self else this

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

    @Transactional
    open fun deliverNext(now: Timestamp): DeliveryOutcome
    {
        val entry = outboxRepository.claimNextPending(now) ?: return DeliveryOutcome.NONE

        val event = runCatching { json.decodeFromString(DomainEvent.serializer(), entry.envelopeJson) }
            .getOrElse { e ->
                entry.status = DomainEventOutboxEntry.STATUS_FAILED
                entry.attemptCount += 1
                entry.lastError = safeError("envelope decode failed", e)
                outboxRepository.update(entry)
                logger.error(
                    "Domain event outbox row id={} type={} has an undecodable envelope; marked FAILED",
                    entry.id,
                    entry.eventType
                )
                return DeliveryOutcome.FAILED
            }

        return try
        {
            eventRouter.routeDurable(event)
            entry.status = DomainEventOutboxEntry.STATUS_DELIVERED
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
                "Domain event outbox delivery failed for id={} type={} attempt={}; will retry",
                entry.id, entry.eventType, entry.attemptCount,
            )
            DeliveryOutcome.RETRIED
        }
    }

    fun backlogHealth(): OutboxBacklogHealth
    {
        val pending = outboxRepository.countByStatus(DomainEventOutboxEntry.STATUS_PENDING)
        val oldest = outboxRepository.oldestPendingCreatedAt()
        val oldestAgeSeconds = oldest?.let {
            Duration.between(it.toInstant(), Instant.now()).seconds.coerceAtLeast(0)
        } ?: 0L
        val repeatedFailures = outboxRepository.countPendingWithAttemptsAtLeast(REPEATED_FAILURE_THRESHOLD)
        val exhausted = outboxRepository.countPendingWithAttemptsAtLeast(EXHAUSTED_THRESHOLD)
        return OutboxBacklogHealth(pending, oldestAgeSeconds, repeatedFailures, exhausted)
    }

    private fun backoff(attempt: Int): Duration
    {
        val exponent = (attempt - 1).coerceIn(0, 30)
        val millis = BASE_BACKOFF.toMillis() shl exponent
        val capped = if (millis <= 0L || millis > MAX_BACKOFF.toMillis()) MAX_BACKOFF.toMillis() else millis
        return Duration.ofMillis(capped)
    }

    private fun safeError(context: String, t: Throwable): String
    {
        val detail = t.message?.take(256) ?: t.javaClass.simpleName
        return "$context: ${t.javaClass.simpleName}: $detail".take(1024)
    }

    enum class DeliveryOutcome
    { NONE, DELIVERED, RETRIED, FAILED }

    companion object
    {
        const val DEFAULT_BATCH_SIZE = 100
        const val MAX_BATCH_SIZE = 1000

        const val REPEATED_FAILURE_THRESHOLD = 3

        const val EXHAUSTED_THRESHOLD = 10

        private val BASE_BACKOFF: Duration = Duration.ofSeconds(30)
        private val MAX_BACKOFF: Duration = Duration.ofHours(1)
    }
}
