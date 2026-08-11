package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Serializes membership activations for organizations with purchased seat capacity.
 *
 * Callers must invoke this inside the transaction that activates the membership or account. The
 * subscription row remains locked until that transaction commits, so concurrent activations see
 * the seat consumed by the transaction that acquired the lock first.
 */
@ApplicationScoped
class OrganizationSeatGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
    private val subscriptionPolicyService: SubscriptionPolicyService,
)
{
    fun enforceAvailableSeat(organizationId: UUID)
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions)
        {
            return
        }

        val context = SubscriptionContext.forOrganization(organizationId)
        val subscription = subscriptionAccessService.resolve(context)
        if (subscription.effectiveSeatCapacity() == null)
        {
            subscriptionAccessService.requireMutationAllowed(context)
            return
        }

        subscriptionPolicyService.findOrganizationPolicyForUpdate(organizationId)
        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireOrganizationSeat(organizationId)
    }
}
