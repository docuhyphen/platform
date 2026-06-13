package com.docuhyphen.app.api.service.workflow.actions

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.workflow.ActionResult
import com.docuhyphen.app.api.service.workflow.WorkflowActionHandler
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Built-in action handler that revokes all active shares on the subject exchange.
 * Useful as a post-rejection cleanup step.
 *
 * Register via `actionHandlerKey = "exchange.revoke-access"` in a workflow step spec.
 */
@ApplicationScoped
class ExchangeRevokeAccessActionHandler : WorkflowActionHandler
{
    private val logger = LoggerFactory.getLogger(ExchangeRevokeAccessActionHandler::class.java)

    @Inject private lateinit var shareService: ShareService

    override fun key() = "exchange.revoke-access"

    override fun execute(instance: WorkflowInstance, step: WorkflowStepInstance): ActionResult
    {
        val exchangeId = instance.subjectResourceId
            ?: return ActionResult(false, "No subjectResourceId on workflow instance ${instance.id}")

        shareService.revokeAllForResource(ResourceType.EXCHANGE, exchangeId)
        logger.info("Revoked all shares for exchange {} via workflow instance {}", exchangeId, instance.id)
        return ActionResult(true)
    }
}
