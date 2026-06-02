package com.docuhyphen.app.api.service.notification.channels

import com.docuhyphen.app.api.model.entity.InAppNotification
import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.repository.InAppNotificationRepository
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
 * Persists an [InAppNotification] row for the recipient — the inbox backing store. A
 * follow-up iteration adds the Quarkus WebSocket endpoint that subscribes to a Redis
 * pub/sub topic and live-pushes new rows to connected clients; this bean already publishes
 * the title/body/payload so the WS layer is a thin wrapper when it lands.
 */
@ApplicationScoped
class InAppChannel : NotificationChannel
{
    private val logger = LoggerFactory.getLogger(InAppChannel::class.java)

    @Inject private lateinit var repository: InAppNotificationRepository

    private val json = Json { encodeDefaults = true }

    override val type: NotificationChannelType = NotificationChannelType.IN_APP

    override fun send(task: DeliveryTask): ChannelSendResult
    {
        return try
        {
            val notif = InAppNotification().apply {
                appUserId = task.recipientUserId
                eventType = task.event.type
                title = titleFor(task)
                body = bodyFor(task)
                payloadJson = json.encodeToString(
                    MapSerializer(String.serializer(), String.serializer()),
                    task.event.payload,
                )
                createdAt = Timestamp.from(Instant.now())
            }
            repository.save(notif)
            ChannelSendResult.Delivered
        }
        catch (t: Throwable)
        {
            logger.warn("InAppChannel persist failed for event {}: {}", task.event.id, t.message)
            ChannelSendResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun titleFor(task: DeliveryTask): String =
        when (task.event.type)
        {
            "session.activated"        -> "Your sharing session was approved"
            "session.rejected"         -> "Your sharing session was rejected"
            "session.approval_requested" -> "A sharing session is awaiting your approval"
            "workflow.step_assigned"   -> "You have a new task awaiting your decision"
            "workflow.escalated"       -> "A workflow step has been escalated to you"
            else                       -> task.event.type
        }.take(255)

    private fun bodyFor(task: DeliveryTask): String?
    {
        val subject = task.event.subject ?: return null
        return "Subject: ${subject.type} ${subject.id}".take(2048)
    }
}

