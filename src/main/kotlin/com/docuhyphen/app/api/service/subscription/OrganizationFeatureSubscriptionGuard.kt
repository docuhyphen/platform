package com.docuhyphen.app.api.service.subscription

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/** Applies a Business feature requirement to a mutation owned by a persisted organization. */
@ApplicationScoped
class OrganizationFeatureSubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
)
{
    fun requireMutation(organizationId: UUID?, feature: PlanFeature)
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions || organizationId == null)
        {
            return
        }

        val context = SubscriptionContext.forOrganization(organizationId)
        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireFeature(context, feature)
    }
}
