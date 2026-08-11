package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.model.entity.WorkflowScope
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/** Applies the commercial workflow-automation boundary without interrupting running instances. */
@ApplicationScoped
class WorkflowSubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
)
{
    fun requireDefinitionMutation(
        scope: WorkflowScope,
        organizationId: UUID?,
        createdByAppUserId: UUID?,
    )
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions || scope == WorkflowScope.APP)
        {
            return
        }

        requireWorkflowMutation(ownerContext(scope, organizationId, createdByAppUserId))
    }

    fun requireInstanceStart(definition: WorkflowDefinition, requestOrganizationId: UUID?)
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions)
        {
            return
        }

        val context = when (definition.scope)
        {
            WorkflowScope.ORG -> ownerContext(
                definition.scope,
                definition.organizationId,
                definition.createdByAppUserId,
            )
            WorkflowScope.PERSONAL -> ownerContext(
                definition.scope,
                definition.organizationId,
                definition.createdByAppUserId,
            )
            WorkflowScope.APP -> requestOrganizationId?.let(SubscriptionContext::forOrganization) ?: return
        }
        requireWorkflowMutation(context)
    }

    private fun requireWorkflowMutation(context: SubscriptionContext)
    {
        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireFeature(context, PlanFeature.WORKFLOW_AUTOMATION)
    }

    private fun ownerContext(
        scope: WorkflowScope,
        organizationId: UUID?,
        createdByAppUserId: UUID?,
    ): SubscriptionContext
    {
        return when (scope)
        {
            WorkflowScope.ORG -> SubscriptionContext.forOrganization(
                requireNotNull(organizationId) { "Organization workflow has no subscription owner" },
            )
            WorkflowScope.PERSONAL -> SubscriptionContext.forUser(
                requireNotNull(createdByAppUserId) { "Personal workflow has no subscription owner" },
            )
            WorkflowScope.APP -> throw IllegalArgumentException("Platform workflows have no customer subscription owner")
        }
    }
}
