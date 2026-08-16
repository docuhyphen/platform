package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowEventOutboxEntry
import com.docuhyphen.app.api.repository.workflow.WorkflowEventOutboxRepository
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * The outbox publisher must persist each workflow event as a single durable row (so it commits
 * atomically with the workflow state mutation) and must not enqueue the same occurrence twice.
 * The publisher uses field injection, so the repository is set reflectively with a Mockito mock.
 */
class WorkflowEventOutboxPublisherTest
{
    private val outboxRepository: WorkflowEventOutboxRepository = mock()
    private lateinit var publisher: WorkflowEventOutboxPublisher

    @BeforeEach
    fun setUp()
    {
        publisher = WorkflowEventOutboxPublisher()
        inject(publisher, "outboxRepository", outboxRepository)
    }

    @Test
    fun `publish enqueues one row carrying the serialized envelope and event metadata`()
    {
        val orgId = UUID.randomUUID()
        val event = DomainEvent(
            type = "workflow.step_assigned",
            organizationId = orgId.toString(),
            subject = DomainEvent.SubjectRef("EXCHANGE", UUID.randomUUID().toString()),
            payload = mapOf("instanceId" to UUID.randomUUID().toString()),
        )
        whenever(outboxRepository.findByEventId(any())).thenReturn(null)

        publisher.publish(event)

        val captor = argumentCaptor<WorkflowEventOutboxEntry>()
        verify(outboxRepository).insert(captor.capture())
        val entry = captor.firstValue
        assertEquals(event.eventUuid(), entry.eventId)
        assertEquals(event.id, entry.idempotencyKey)
        assertEquals("workflow.step_assigned", entry.eventType)
        assertEquals(orgId, entry.organizationId)
        assertEquals(WorkflowEventOutboxEntry.STATUS_PENDING, entry.status)
        assertEquals(0, entry.attemptCount)

        // The persisted envelope must round-trip back to the original event.
        val decoded = DomainEventJson.instance.decodeFromString(DomainEvent.serializer(), entry.envelopeJson)
        assertEquals(event.id, decoded.id)
        assertEquals(event.type, decoded.type)
        assertNotNull(decoded.subject)
        assertEquals(event.payload, decoded.payload)
    }

    @Test
    fun `publish is idempotent when the same event id is already enqueued`()
    {
        val event = DomainEvent(type = "workflow.completed")
        whenever(outboxRepository.findByEventId(event.eventUuid())).thenReturn(WorkflowEventOutboxEntry())

        publisher.publish(event)

        verify(outboxRepository, never()).insert(any())
    }

    private fun inject(target: Any, field: String, value: Any)
    {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        f.set(target, value)
    }
}
