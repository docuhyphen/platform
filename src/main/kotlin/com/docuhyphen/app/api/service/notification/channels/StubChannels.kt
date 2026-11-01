package com.docuhyphen.app.api.service.notification.channels

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.model.notification.ChannelSendResult
import com.docuhyphen.app.api.model.notification.DeliveryTask
import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory

abstract class StubChannel(override val type: NotificationChannelType) : NotificationChannel
{
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun send(task: DeliveryTask): ChannelSendResult
    {
        logger.info("[stub-{}] would deliver event {} to user {}", type, task.event.type, task.recipientUserId)
        return ChannelSendResult.Delivered
    }
}

@ApplicationScoped
class SmsChannel : StubChannel(NotificationChannelType.SMS)
@ApplicationScoped
class SlackChannel : StubChannel(NotificationChannelType.SLACK)
@ApplicationScoped
class TeamsChannel : StubChannel(NotificationChannelType.TEAMS)
@ApplicationScoped
class WhatsAppChannel : StubChannel(NotificationChannelType.WHATSAPP)

