package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.service.application.WebhookDeliveryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * ACTION step handler that delivers an outbound webhook payload.
 *
 * The step spec must have:
 * - `actionHandlerKey = "WEBHOOK_DELIVER"`
 * - `webhookEndpointId`: UUID of the [com.docuhyphen.app.api.model.entity.WorkflowWebhookEndpoint] to call
 * - `webhookEventType`: event type string included in the payload (e.g. "exchange.activated")
 *
 * A non-2xx response or network error causes the step to fail (ActionResult.success = false),
 * which rejects the workflow instance.
 */
@ApplicationScoped
class WebhookWorkflowActionHandler @Inject constructor(
    private val webhookDeliveryService: WebhookDeliveryService,
) : WorkflowActionHandler
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(WebhookWorkflowActionHandler::class.java)
        const val KEY = "WEBHOOK_DELIVER"
    }

    override fun key(): String = KEY

    override fun execute(instance: WorkflowInstance, step: WorkflowStepInstance): ActionResult
    {
        val spec = runCatching { WorkflowSpecJson.decodeStep(step.specSnapshotJson) }.getOrElse { e ->
            logger.error("Failed to decode step spec for instance ${instance.id}", e)
            return ActionResult(success = false, reason = "Invalid step spec: ${e.message}")
        }

        val endpointIdStr = spec.webhookEndpointId
            ?: return ActionResult(success = false, reason = "webhookEndpointId not set in step spec")

        val endpointId = runCatching { UUID.fromString(endpointIdStr) }.getOrElse {
            return ActionResult(success = false, reason = "webhookEndpointId is not a valid UUID: $endpointIdStr")
        }

        val eventType = spec.webhookEventType ?: "workflow.action"

        val result = webhookDeliveryService.deliver(endpointId, eventType, instance)
        return ActionResult(success = result.success, reason = result.reason)
    }
}
