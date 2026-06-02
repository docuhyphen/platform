package com.docuhyphen.app.api.service.notification.channels

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.service.notification.ChannelSendResult
import com.docuhyphen.app.api.service.notification.DeliveryTask
import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory

/**
 * Stubs for SMS / Slack / Teams / WhatsApp delivery. Each bean is wired into the
 * dispatcher's `Instance<NotificationChannel>` injection but currently logs and returns
 * `Delivered` so the pipeline is observable end-to-end without external dependencies.
 *
 * Real implementations land in a later iteration once the org-channel OAuth flows
 * ([com.docuhyphen.app.api.model.entity.OrganizationNotificationChannel]) and per-user
 * [com.docuhyphen.app.api.model.entity.UserChannelLink] resolution are in place.
 */
abstract class StubChannel(override val type: NotificationChannelType) : NotificationChannel
{
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun send(task: DeliveryTask): ChannelSendResult
    {
        logger.info("[stub-{}] would deliver event {} to user {}", type, task.event.type, task.recipientUserId)
        return ChannelSendResult.Delivered
    }
}

@ApplicationScoped class SmsChannel       : StubChannel(NotificationChannelType.SMS)
@ApplicationScoped class SlackChannel     : StubChannel(NotificationChannelType.SLACK)
@ApplicationScoped class TeamsChannel     : StubChannel(NotificationChannelType.TEAMS)
@ApplicationScoped class WhatsAppChannel  : StubChannel(NotificationChannelType.WHATSAPP)

