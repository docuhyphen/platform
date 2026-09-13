package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.DomainEventOutboxEntry
import com.docuhyphen.app.api.repository.notification.DomainEventOutboxRepository
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
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
 * The outbox publisher must persist each required event as a single durable row so it commits
 * atomically with the state mutation and must not enqueue the same occurrence twice.
 * The publisher uses field injection, so the repository is set reflectively with a Mockito mock.
 */
class DomainEventOutboxPublisherTest
{
    private val outboxRepository: DomainEventOutboxRepository = mock()
    private lateinit var publisher: DomainEventOutboxPublisher

    @BeforeEach
    fun setUp()
    {
        publisher = DomainEventOutboxPublisher()
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

        val captor = argumentCaptor<DomainEventOutboxEntry>()
        verify(outboxRepository).insert(captor.capture())
        val entry = captor.firstValue
        assertEquals(event.eventUuid(), entry.eventId)
        assertEquals("workflow:${event.id}", entry.idempotencyKey)
        assertEquals("workflow.step_assigned", entry.eventType)
        assertEquals(orgId, entry.organizationId)
        assertEquals("ORGANIZATION", entry.ownerKind)
        assertEquals(orgId, entry.ownerId)
        assertEquals(DomainEventOutboxEntry.STATUS_PENDING, entry.status)
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
        whenever(outboxRepository.findByEventId(event.eventUuid())).thenReturn(DomainEventOutboxEntry())

        publisher.publish(event)

        verify(outboxRepository, never()).insert(any())
    }

    @Test
    fun `publish accepts an explicit personal owner without an organization id`()
    {
        val ownerUserId = UUID.randomUUID()
        val event = DomainEvent(
            type = "information_request.request.create",
            subject = DomainEvent.SubjectRef("INFORMATION_REQUEST", UUID.randomUUID().toString()),
        )
        whenever(outboxRepository.findByEventId(any())).thenReturn(null)

        publisher.publish(event, AuditOwnerScope.Personal(ownerUserId))

        val captor = argumentCaptor<DomainEventOutboxEntry>()
        verify(outboxRepository).insert(captor.capture())
        val entry = captor.firstValue
        assertEquals("information_request:${event.id}", entry.idempotencyKey)
        assertEquals("USER", entry.ownerKind)
        assertEquals(ownerUserId, entry.ownerId)
        assertEquals(null, entry.organizationId)
    }

    private fun inject(target: Any, field: String, value: Any)
    {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        f.set(target, value)
    }
}
