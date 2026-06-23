package com.docuhyphen.app.api.service.notification.channels

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.MarkdownRenderer
import com.docuhyphen.app.api.service.communication.templates.EmailTemplateRenderer
import com.docuhyphen.app.api.service.notification.ChannelSendResult
import com.docuhyphen.app.api.service.notification.DeliveryTask
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
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
    @Inject private lateinit var templateRenderer: EmailTemplateRenderer
    @Inject private lateinit var markdownRenderer: MarkdownRenderer

    @ConfigProperty(name = "app.url", defaultValue = "https://app.docuhyphen.com")
    private lateinit var appUrl: String

    @ConfigProperty(name = "app.name", defaultValue = "DocuHyphen")
    private lateinit var appName: String

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

        val (subject, body, useHtml) = when (task.event.type)
        {
            "workflow.step_assigned" -> renderStepAssigned(task)
            "workflow.notification"  -> renderWorkflowNotification(task)
            else -> Triple(
                "[${appName}] ${task.event.type}",
                buildString {
                    appendLine("Event: ${task.event.type}")
                    task.event.subject?.let { appendLine("Subject: ${it.type} ${it.id}") }
                    task.event.actor?.let { appendLine("Actor: ${it.kind} ${it.id}") }
                    if (task.event.payload.isNotEmpty())
                    {
                        appendLine()
                        appendLine("Details:")
                        task.event.payload.forEach { (k, v) -> appendLine("  $k: $v") }
                    }
                },
                false,
            )
        }

        return try
        {
            emailService.sendEmail(to, subject, body, useHtml = useHtml)
            ChannelSendResult.Delivered
        }
        catch (t: Throwable)
        {
            logger.warn("EmailChannel send failed for event {} to {}: {}", task.event.id, to, t.message)
            ChannelSendResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun renderWorkflowNotification(task: DeliveryTask): Triple<String, String, Boolean>
    {
        val renderedSubject = task.event.payload["renderedSubject"]
        val renderedBody = task.event.payload["renderedBody"]

        if (renderedSubject != null && renderedBody != null)
        {
            val html = markdownRenderer.toHtml(renderedBody)
            val wrappedHtml = runCatching {
                templateRenderer.render(
                    "communication-wrapper.ftl",
                    mapOf(
                        "appName" to appName,
                        "appUrl" to appUrl,
                        "htmlBody" to html,
                        "emailTitle" to renderedSubject,
                    )
                )
            }.getOrElse { t ->
                logger.warn("Failed to render communication-wrapper: {}", t.message)
                html
            }
            return Triple(renderedSubject, wrappedHtml, true)
        }

        return Triple(
            "[${appName}] Workflow Notification",
            "You have a workflow notification in ${appName}. Please sign in to view it.",
            false,
        )
    }

    private fun renderStepAssigned(task: DeliveryTask): Triple<String, String, Boolean>
    {
        val payload = task.event.payload
        val exchangeName = payload["exchangeName"]?.takeIf { it.isNotBlank() }
        val subject = if (exchangeName != null)
            "[${appName}] Action required: approval request for \"${exchangeName}\""
        else
            "[${appName}] Action required: you have a pending approval"

        val model = buildMap<String, Any> {
            put("appName", appName)
            put("appUrl", appUrl)
            exchangeName?.let { put("exchangeName", it) }
            payload["initiatorName"]?.takeIf { it.isNotBlank() }?.let { put("initiatorName", it) }
            payload["workflowName"]?.takeIf { it.isNotBlank() }?.let { put("workflowName", it) }
        }

        val html = runCatching { templateRenderer.render("workflow-step-assigned.ftl", model) }
            .getOrElse { t ->
                logger.warn("Failed to render workflow-step-assigned template: {}", t.message)
                "<p>You have a pending approval in ${appName}. Please sign in to review it.</p>"
            }

        return Triple(subject, html, true)
    }
}

