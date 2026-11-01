package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

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
