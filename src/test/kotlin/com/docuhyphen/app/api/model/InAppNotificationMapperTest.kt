package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.InAppNotification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InAppNotificationMapperTest
{
    private val mapper = InAppNotificationMapper()

    @Test
    fun `maps persisted notification payload into realtime dto`()
    {
        val exchangeId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID()
        val timestamp = Timestamp.from(Instant.parse("2026-07-15T12:00:00Z"))
        val notification = InAppNotification().apply {
            appUserId = userId
            eventType = "workflow.step_assigned"
            title = "Approval required"
            body = "Review this Exchange"
            payloadJson = """{"subjectType":"EXCHANGE","subjectId":"$exchangeId","stepInstanceId":"step-1"}"""
            createdAt = timestamp
        }

        val result = mapper.toDto(notification)

        assertEquals(notification.id.toString(), result.id)
        assertEquals("workflow.step_assigned", result.type)
        assertEquals("Review this Exchange", result.message)
        assertEquals(exchangeId, result.exchangeId)
        assertEquals("step-1", result.data["stepInstanceId"])
        assertEquals(userId.toString(), result.userId)
        assertEquals(timestamp, result.timestamp)
    }
}
