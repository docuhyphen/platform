package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.DomainEventOutboxEntry
import com.docuhyphen.app.api.model.notification.DomainEventOwnerColumns
import com.docuhyphen.app.api.repository.notification.DomainEventOutboxRepository
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
@TransactionalEventSink
class DomainEventOutboxPublisher : DomainEventPublisher
{
    private val logger = LoggerFactory.getLogger(DomainEventOutboxPublisher::class.java)

    @Inject
    private lateinit var outboxRepository: DomainEventOutboxRepository

    private val json = DomainEventJson.instance

    override fun publish(event: DomainEvent) = publish(event, ownerFromEvent(event))

    override fun publish(event: DomainEvent, owner: AuditOwnerScope)
    {
        val eventId = runCatching { event.eventUuid() }.getOrElse { UUID.randomUUID() }

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
            ownerKind = ownerColumns.ownerKind
            ownerId = ownerColumns.ownerId
            orderingKey = event.orderingKey?.trim()?.takeIf { it.isNotBlank() }
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

    private fun ownerColumns(owner: AuditOwnerScope): DomainEventOwnerColumns = when (owner)
    {
        AuditOwnerScope.Platform -> DomainEventOwnerColumns("PLATFORM", null)
        is AuditOwnerScope.Organization -> DomainEventOwnerColumns("ORGANIZATION", owner.organizationId)
        is AuditOwnerScope.Personal -> DomainEventOwnerColumns("USER", owner.userId)
    }
}
