package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.model.subscription.SubscriptionContext

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class ExchangeFeatureSubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
)
{
    fun requireDocumentVersionHistory(exchange: Exchange)
    {
        val context = ownerContext(exchange) ?: return

        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireFeature(context, PlanFeature.DOCUMENT_VERSION_HISTORY)
    }

    fun requireAdvancedAccessControls(
        exchange: Exchange,
        constraintsJson: String?,
        expiresAtEpochMillis: Long?,
    )
    {
        if (constraintsJson.isNullOrBlank() && expiresAtEpochMillis == null)
        {
            return
        }

        val context = ownerContext(exchange) ?: return

        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireFeature(context, PlanFeature.ADVANCED_ACCESS_CONTROLS)
    }

    private fun ownerContext(exchange: Exchange): SubscriptionContext?
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions)
        {
            return null
        }

        return SubscriptionContext.forOwner(exchange.ownerUserId, exchange.ownerOrganizationId)
    }
}

