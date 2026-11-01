package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant
import java.util.*

@ApplicationScoped
class ExchangeInitiationSubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
    private val subscriptionPolicyService: SubscriptionPolicyService,
)
{
    fun enforceInitiation(
        initiatorId: UUID,
        activeOrganizationId: UUID?,
        additionalParticipants: Int,
        recipientConstraintsJson: String? = null,
        at: Instant = Instant.now(),
    )
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions)
        {
            return
        }

        val context = activeOrganizationId
            ?.let { SubscriptionContext.forOrganization(it) }
            ?: SubscriptionContext.forUser(initiatorId)

        lockCappedSubject(context)

        subscriptionAccessService.requireMutationAllowed(context, at)
        subscriptionAccessService.requireExchangeCapacity(context, at)
        subscriptionAccessService.requireParticipantCapacity(context, additionalParticipants)
        if (!recipientConstraintsJson.isNullOrBlank())
        {
            subscriptionAccessService.requireFeature(context, PlanFeature.ADVANCED_ACCESS_CONTROLS)
        }
    }

    private fun lockCappedSubject(context: SubscriptionContext)
    {
        if (context.ownerType != SubscriptionOwnerType.USER)
        {
            return
        }

        val limits = subscriptionAccessService.resolve(context).limits
        if (!limits.hasExchangeCreationCap && !limits.hasOpenExchangeCap)
        {
            return
        }

        subscriptionPolicyService.findUserPolicyForUpdate(context.ownerId)
    }
}

