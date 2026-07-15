package com.docuhyphen.app.api.service.notification.channels

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.service.notification.DeliveryTask
import com.docuhyphen.app.api.service.notification.DomainEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.UUID

class InAppChannelContentTest
{
    private val channel = InAppChannel()

    @Test
    fun `approval notification uses Exchange and requester names without technical identifiers`()
    {
        val exchangeId = UUID.randomUUID().toString()
        val task = task(
            type = "workflow.step_assigned",
            subjectType = "EXCHANGE",
            subjectId = exchangeId,
            payload = mapOf(
                "exchangeName" to "Annual records",
                "initiatorName" to "Amina Patel",
                "stepInstanceId" to UUID.randomUUID().toString(),
            ),
        )

        val body = channel.bodyFor(task)

        assertEquals("Approval required", channel.titleFor(task))
        assertEquals(
            "Amina Patel requested your approval for Exchange \"Annual records\".",
            body,
        )
        assertFalse(body.orEmpty().contains("EXCHANGE"))
        assertFalse(body.orEmpty().contains(exchangeId))
    }

    @Test
    fun `unknown notification hides subject enum and id`()
    {
        val subjectId = UUID.randomUUID().toString()
        val task = task(
            type = "internal.event_code",
            subjectType = "EXCHANGE",
            subjectId = subjectId,
        )

        val body = channel.bodyFor(task)

        assertEquals("Notification", channel.titleFor(task))
        assertEquals("Open the related Exchange to view more information.", body)
        assertFalse(body.orEmpty().contains(subjectId))
    }

    private fun task(
        type: String,
        subjectType: String,
        subjectId: String,
        payload: Map<String, String> = emptyMap(),
    ): DeliveryTask = DeliveryTask(
        event = DomainEvent(
            type = type,
            subject = DomainEvent.SubjectRef(subjectType, subjectId),
            payload = payload,
        ),
        recipientUserId = UUID.randomUUID(),
        channel = NotificationChannelType.IN_APP,
    )
}
