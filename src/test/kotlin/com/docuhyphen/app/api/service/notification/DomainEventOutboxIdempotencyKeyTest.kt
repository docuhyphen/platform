package com.docuhyphen.app.api.service.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class DomainEventOutboxIdempotencyKeyTest
{
    @Test
    fun `forEvent prefixes the event id with the event type namespace`()
    {
        val eventId = UUID.randomUUID().toString()

        val workflowKey = DomainEventOutboxIdempotencyKey.forEvent(
            DomainEvent(id = eventId, type = "workflow.step_assigned"),
        )
        val informationRequestKey = DomainEventOutboxIdempotencyKey.forEvent(
            DomainEvent(id = eventId, type = "information_request.request.create"),
        )

        assertEquals("workflow:$eventId", workflowKey)
        assertEquals("information_request:$eventId", informationRequestKey)
        assertNotEquals(workflowKey, informationRequestKey)
    }
}
