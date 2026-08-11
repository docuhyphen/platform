package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import com.docuhyphen.app.api.service.subscription.PlanFeature
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant
import java.util.UUID

/**
 * Applies the commercial allowances that govern creating an Exchange.
 *
 * This is deliberately separate from the initiation service so that the initiation flow keeps
 * one responsibility and the commercial rules can be reasoned about, and tested, on their own.
 *
 * The caller must already be authorized. Authorization decides whether this person may act;
 * these checks decide whether the paying subject has bought the allowance being consumed.
 *
 * Allowance checks must run inside the same transaction as the Exchange insert. Where the
 * subject is capped, the subscription row is locked first, so two requests racing for the last
 * remaining allowance are serialized and only one of them can consume it.
 */
@ApplicationScoped
class ExchangeInitiationSubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
    private val subscriptionPolicyService: SubscriptionPolicyService,
)
{
    /**
     * Refuses initiation when the paying subject may not create this Exchange.
     *
     * @param initiatorId the authenticated user creating the Exchange
     * @param activeOrganizationId the selected organization, or null when acting personally
     * @param additionalParticipants people invited beyond the required primary recipient
     */
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

    /**
     * Takes a row-level write lock on the subscription of a subject whose Exchange allowances are
     * capped, so the counts that follow cannot be undercounted by a concurrent creation. Subjects
     * with no capped Exchange allowance are left unlocked because nothing can be over-consumed.
     */
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

