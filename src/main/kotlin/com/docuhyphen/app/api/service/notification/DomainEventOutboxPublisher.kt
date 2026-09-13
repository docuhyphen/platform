package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.DomainEventOutboxEntry
import com.docuhyphen.app.api.repository.notification.DomainEventOutboxRepository
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Durable [DomainEventPublisher] for required domain events. Rather than routing an event
 * synchronously on the caller's transaction, it serializes the envelope into a
 * `workflow_event_outbox` row in that same transaction. The row commits atomically with the
 * authoritative state mutation, so a required event can never be lost by a crash between the state
 * commit and routing. [DomainEventDispatcher] delivers committed rows after commit.
 *
 * Selected by the [TransactionalEventSink] qualifier so callers opt into durable delivery; every other
 * service keeps the default in-process publisher.
 */
@ApplicationScoped
@TransactionalEventSink
class DomainEventOutboxPublisher : DomainEventPublisher
{
    private val logger = LoggerFactory.getLogger(DomainEventOutboxPublisher::class.java)

    @Inject private lateinit var outboxRepository: DomainEventOutboxRepository

    private val json = DomainEventJson.instance

    override fun publish(event: DomainEvent) = publish(event, ownerFromEvent(event))

    override fun publish(event: DomainEvent, owner: AuditOwnerScope)
    {
        val eventId = runCatching { event.eventUuid() }.getOrElse { UUID.randomUUID() }

        // Belt-and-braces dedup: a repeated enqueue for the same occurrence must not write twice.
        // The event_id unique constraint is the authoritative guard.
        if (outboxRepository.findByEventId(eventId) != null)
        {
            logger.debug("Domain event id={} type={} already enqueued; skipping", event.id, event.type)
            return
        }

        val ownerColumns = ownerColumns(owner)
        val now = Timestamp.from(Instant.now())
        val entry = DomainEventOutboxEntry().apply {
            this.eventId = eventId
            idempotencyKey = DomainEventOutboxIdempotencyKey.forEvent(event)
            eventType = event.type
            organizationId = ownerColumns.organizationId
            ownerKind = ownerColumns.ownerKind
            ownerId = ownerColumns.ownerId
            envelopeJson = json.encodeToString(DomainEvent.serializer(), event)
            createdAt = now
            nextAttemptAt = now
        }
        outboxRepository.insert(entry)
        logger.debug("Enqueued domain event id={} type={} to outbox", event.id, event.type)
    }

    private fun ownerFromEvent(event: DomainEvent): AuditOwnerScope =
        event.organizationId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let(AuditOwnerScope::Organization)
            ?: AuditOwnerScope.Platform

    private fun ownerColumns(owner: AuditOwnerScope): OwnerColumns = when (owner)
    {
        AuditOwnerScope.Platform -> OwnerColumns("PLATFORM", null, null)
        is AuditOwnerScope.Organization -> OwnerColumns("ORGANIZATION", owner.organizationId, owner.organizationId)
        is AuditOwnerScope.Personal -> OwnerColumns("USER", owner.userId, null)
    }

    private data class OwnerColumns(
        val ownerKind: String,
        val ownerId: UUID?,
        val organizationId: UUID?,
    )
}
