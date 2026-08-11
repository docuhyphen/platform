package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/** Applies the Business subscription requirement to Fields and Schema mutations. */
@ApplicationScoped
class BusinessFieldsSubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
)
{
    fun requireConfigurationMutation(scopeKind: FieldScopeKind, organizationId: UUID?)
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions ||
            scopeKind == FieldScopeKind.PLATFORM)
        {
            return
        }

        val ownerOrganizationId = requireNotNull(organizationId) {
            "Organization-scoped Fields configuration has no subscription owner"
        }
        requireMutation(SubscriptionContext.forOrganization(ownerOrganizationId))
    }

    fun requireResourceMutation(context: SubscriptionContext?)
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions)
        {
            return
        }

        requireMutation(requireNotNull(context) { "Field resource has no subscription owner" })
    }

    private fun requireMutation(context: SubscriptionContext)
    {
        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireFeature(context, PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS)
    }
}
