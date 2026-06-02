package com.docuhyphen.app.api.service.notification.channels

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.notification.ChannelSendResult
import com.docuhyphen.app.api.service.notification.DeliveryTask
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Email delivery. Resolves the recipient's [com.docuhyphen.app.api.model.entity.AppUser.email]
 * and delegates to the existing [EmailService.sendEmail] (which already wraps AWS SES).
 *
 * Iteration 3 sends plain text; the [com.docuhyphen.app.api.service.communication.EmailTemplateRenderer]
 * pipeline is the planned integration once event-specific templates are added.
 */
@ApplicationScoped
class EmailChannel : NotificationChannel
{
    private val logger = LoggerFactory.getLogger(EmailChannel::class.java)

    @Inject private lateinit var appUserRepository: AppUserRepository
    @Inject private lateinit var emailService: EmailService

    override val type: NotificationChannelType = NotificationChannelType.EMAIL

    override fun send(task: DeliveryTask): ChannelSendResult
    {
        val user = appUserRepository.findById(task.recipientUserId)
            ?: return ChannelSendResult.Failed("recipient user ${task.recipientUserId} not found")
        val to = runCatching { user.email }.getOrNull()
        if (to.isNullOrBlank())
        {
            return ChannelSendResult.Failed("recipient ${user.id} has no email")
        }

        val subject = "[DocuHyphen] ${task.event.type}"
        val body = buildString {
            appendLine("Event: ${task.event.type}")
            task.event.subject?.let { appendLine("Subject: ${it.type} ${it.id}") }
            task.event.actor?.let { appendLine("Actor: ${it.kind} ${it.id}") }
            if (task.event.payload.isNotEmpty())
            {
                appendLine()
                appendLine("Details:")
                task.event.payload.forEach { (k, v) -> appendLine("  $k: $v") }
            }
        }

        return try
        {
            emailService.sendEmail(to, subject, body, useHtml = false)
            ChannelSendResult.Delivered
        }
        catch (t: Throwable)
        {
            logger.warn("EmailChannel send failed for event {} to {}: {}", task.event.id, to, t.message)
            ChannelSendResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }
}

