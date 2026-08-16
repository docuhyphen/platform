package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowEventOutboxEntry
import com.docuhyphen.app.api.repository.workflow.WorkflowEventOutboxRepository
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventJson
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.notification.WorkflowEventSink
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Durable [DomainEventPublisher] for the workflow engine. Rather than routing an event synchronously
 * on the engine's transaction, it serializes the envelope into a `workflow_event_outbox` row in that
 * same transaction. The row commits atomically with the authoritative workflow state mutation, so a
 * required lifecycle or terminal event can never be lost by a crash between the state commit and
 * routing. [WorkflowEventDispatcher] delivers committed rows after commit.
 *
 * Selected by the [WorkflowEventSink] qualifier so it is used only by the engine; every other
 * service keeps the default in-process publisher.
 */
@ApplicationScoped
@WorkflowEventSink
class WorkflowEventOutboxPublisher : DomainEventPublisher
{
    private val logger = LoggerFactory.getLogger(WorkflowEventOutboxPublisher::class.java)

    @Inject private lateinit var outboxRepository: WorkflowEventOutboxRepository

    private val json = DomainEventJson.instance

    override fun publish(event: DomainEvent)
    {
        val eventId = runCatching { event.eventUuid() }.getOrElse { UUID.randomUUID() }

        // Belt-and-braces dedup: a repeated enqueue for the same occurrence must not write twice.
        // The event_id unique constraint is the authoritative guard.
        if (outboxRepository.findByEventId(eventId) != null)
        {
            logger.debug("Workflow event id={} type={} already enqueued; skipping", event.id, event.type)
            return
        }

        val now = Timestamp.from(Instant.now())
        val entry = WorkflowEventOutboxEntry().apply {
            this.eventId = eventId
            idempotencyKey = event.id
            eventType = event.type
            organizationId = event.organizationId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            envelopeJson = json.encodeToString(DomainEvent.serializer(), event)
            createdAt = now
            nextAttemptAt = now
        }
        outboxRepository.insert(entry)
        logger.debug("Enqueued workflow event id={} type={} to outbox", event.id, event.type)
    }
}
