package com.docuhyphen.app.api.service.workflow.actions

import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.service.communication.AppNotificationService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.workflow.ActionResult
import com.docuhyphen.app.api.service.workflow.WorkflowActionHandler
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Built-in action handler that sends a reminder notification and email to the primary
 * recipient of the subject exchange. The recipient user id is read from the workflow
 * instance's `subjectDataJson` field (`recipientId` key).
 *
 * Register via `actionHandlerKey = "exchange.send-reminder"` in a workflow step spec.
 */
@ApplicationScoped
class ExchangeSendReminderActionHandler : WorkflowActionHandler
{
    private val logger = LoggerFactory.getLogger(ExchangeSendReminderActionHandler::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    @Inject private lateinit var appUserRepository: AppUserRepository
    @Inject private lateinit var appNotificationService: AppNotificationService
    @Inject private lateinit var emailService: EmailService

    override fun key() = "exchange.send-reminder"

    override fun execute(instance: WorkflowInstance, step: WorkflowStepInstance): ActionResult
    {
        val data = subjectData(instance)
        val recipientIdStr = data["recipientId"]
            ?: return ActionResult(false, "No recipientId in subjectDataJson for instance ${instance.id}")

        val recipientId = runCatching { UUID.fromString(recipientIdStr) }.getOrNull()
            ?: return ActionResult(false, "recipientId '$recipientIdStr' is not a valid UUID")

        val user = appUserRepository.findById(recipientId)
            ?: return ActionResult(false, "Recipient user $recipientId not found")

        val subject = "Reminder: Your action is required"
        val body = "You have a pending exchange that requires your attention. Please log in to DocuHyphen to review it."

        appNotificationService.sendNotification(recipientId.toString(), subject, body)
        try
        {
            emailService.sendEmail(user.email, subject, body)
        }
        catch (e: Exception)
        {
            logger.warn("Failed to send reminder email to {}: {}", user.email, e.message)
        }

        logger.info("Reminder sent to recipient {} for workflow instance {}", recipientId, instance.id)
        return ActionResult(true)
    }

    private fun subjectData(instance: WorkflowInstance): Map<String, String> =
        runCatching {
            instance.subjectDataJson?.let {
                json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it)
            }
        }.getOrNull() ?: emptyMap()
}
