package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class VariableSubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
)
{
    fun requirePersonalManagement(appUserId: UUID)
    {
        require(SubscriptionContext.forUser(appUserId))
    }

    fun requireOrganizationManagement(organizationId: UUID)
    {
        require(SubscriptionContext.forOrganization(organizationId))
    }

    private fun require(context: SubscriptionContext)
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions)
        {
            return
        }

        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireFeature(context, PlanFeature.VARIABLES_AND_SEQUENCES)
    }
}

