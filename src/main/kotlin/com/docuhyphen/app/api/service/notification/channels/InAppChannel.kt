package com.docuhyphen.app.api.service.notification.channels

import com.docuhyphen.app.api.model.InAppNotificationMapper
import com.docuhyphen.app.api.model.entity.InAppNotification
import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.repository.InAppNotificationRepository
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.service.notification.ChannelSendResult
import com.docuhyphen.app.api.service.notification.DeliveryTask
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant

/**
 * Persists an [InAppNotification] row and pushes the same DTO to connected clients.
 */
@ApplicationScoped
class InAppChannel : NotificationChannel
{
    private val logger = LoggerFactory.getLogger(InAppChannel::class.java)

    @Inject
    private lateinit var repository: InAppNotificationRepository
    @Inject
    private lateinit var mapper: InAppNotificationMapper
    @Inject
    private lateinit var realtimeEventService: RealtimeEventService

    private val json = Json { encodeDefaults = true }

    override val type: NotificationChannelType = NotificationChannelType.IN_APP

    override fun send(task: DeliveryTask): ChannelSendResult
    {
        return try
        {
            val payload = task.event.payload.toMutableMap().apply {
                task.event.subject?.let { subject ->
                    putIfAbsent("subjectType", subject.type)
                    putIfAbsent("subjectId", subject.id.toString())
                }
            }
            val notification = InAppNotification().apply {
                appUserId = task.recipientUserId
                eventType = task.event.type
                title = titleFor(task)
                body = bodyFor(task)
                payloadJson = json.encodeToString(
                    MapSerializer(String.serializer(), String.serializer()),
                    payload,
                )
                createdAt = Timestamp.from(Instant.now())
            }
            val saved = repository.save(notification)
            realtimeEventService.broadcastNotificationToUser(task.recipientUserId, mapper.toDto(saved))
            ChannelSendResult.Delivered
        }
        catch (t: Throwable)
        {
            logger.warn("InAppChannel persist failed for event {}: {}", task.event.id, t.message)
            ChannelSendResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    internal fun titleFor(task: DeliveryTask): String =
        when (task.event.type)
        {
            "session.activated" -> "Your Exchange was approved"
            "session.rejected" -> "Your Exchange was rejected"
            "session.approval_requested" -> "Exchange approval required"
            "workflow.step_assigned" -> "Approval required"
            "workflow.escalated" -> "Approval escalated"
            "workflow.notification" ->
                task.event.payload["renderedSubject"]?.takeIf { it.isNotBlank() }
                    ?: "Workflow notification"

            else -> "Notification"
        }.take(255)

    internal fun bodyFor(task: DeliveryTask): String?
    {
        val payload = task.event.payload
        val body = when (task.event.type)
        {
            "workflow.notification" ->
                payload["renderedBody"]?.takeIf { it.isNotBlank() }
                    ?: relatedItemMessage(task)

            "workflow.step_assigned" -> approvalRequiredMessage(payload)
            "workflow.escalated" -> approvalEscalatedMessage(payload)
            "session.approval_requested" -> exchangeMessage(
                payload,
                namedMessage = { name -> "Review and decide whether to approve Exchange \"$name\"." },
                fallback = "Review and decide whether to approve the related Exchange.",
            )

            "session.activated" -> exchangeMessage(
                payload,
                namedMessage = { name -> "Exchange \"$name\" was approved and is now active." },
                fallback = "The related Exchange was approved and is now active.",
            )

            "session.rejected" -> exchangeMessage(
                payload,
                namedMessage = { name -> "Exchange \"$name\" was not approved." },
                fallback = "The related Exchange was not approved.",
            )

            else -> relatedItemMessage(task)
        }
        return body.take(2048)
    }

    private fun approvalRequiredMessage(payload: Map<String, String>): String
    {
        val exchangeName = payload["exchangeName"]?.takeIf { it.isNotBlank() }
        val initiatorName = payload["initiatorName"]?.takeIf { it.isNotBlank() }
        return when
        {
            exchangeName != null && initiatorName != null ->
                "$initiatorName requested your approval for Exchange \"$exchangeName\"."

            exchangeName != null ->
                "Review and decide on the approval request for Exchange \"$exchangeName\"."

            initiatorName != null ->
                "$initiatorName requested your approval."

            else -> "Review and decide on the pending approval."
        }
    }

    private fun approvalEscalatedMessage(payload: Map<String, String>): String =
        exchangeMessage(
            payload,
            namedMessage = { name -> "An overdue approval for Exchange \"$name\" has been assigned to you." },
            fallback = "An overdue approval has been assigned to you for review.",
        )

    private fun exchangeMessage(
        payload: Map<String, String>,
        namedMessage: (String) -> String,
        fallback: String,
    ): String = payload["exchangeName"]
        ?.takeIf { it.isNotBlank() }
        ?.let(namedMessage)
        ?: fallback

    private fun relatedItemMessage(task: DeliveryTask): String =
        when (task.event.subject?.type)
        {
            "EXCHANGE" -> "Open the related Exchange to view more information."
            "DOCUMENT" -> "Open the related document to view more information."
            else -> "Open this notification to view more information."
        }
}

