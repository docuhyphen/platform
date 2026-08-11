package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Applies the commercial allowance that governs authoring variables and sequences.
 *
 * Only authoring is checked. Listing and resolving what already exists stays open so a plan
 * change never breaks a Blueprint, document, or Exchange that already refers to a value, and so
 * nothing a subject created is ever hidden from them.
 */
@ApplicationScoped
class VariableSubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
)
{
    /** Authoring a value that belongs to one person. */
    fun requirePersonalManagement(appUserId: UUID)
    {
        require(SubscriptionContext.forUser(appUserId))
    }

    /** Authoring a value that belongs to an organization. */
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



