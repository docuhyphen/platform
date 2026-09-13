package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialFactory
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Applies the two commercial and operational decisions about Information Requests to runtime
 * requests, answered for the owner of the parent Exchange rather than for whichever organization the
 * caller currently has selected.
 *
 * This mirrors [InformationRequestTemplateEntitlementGuard], but a runtime request never names a
 * scope kind of its own: it is always exactly as personal or organization-owned as its parent
 * Exchange, so the owner is read directly from that Exchange.
 */
@ApplicationScoped
class InformationRequestEntitlementGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
)
{
    /** Reads answer to both gates too, so an unreleased capability exposes no runtime request. */
    fun requireRequestAccess(exchange: Exchange)
    {
        subscriptionAccessService.requireFeature(owner(exchange), PlanFeature.INFORMATION_REQUESTS)
    }

    fun requireRequestMutation(exchange: Exchange)
    {
        val owner = owner(exchange)
        subscriptionAccessService.requireMutationAllowed(owner)
        subscriptionAccessService.requireFeature(owner, PlanFeature.INFORMATION_REQUESTS)
    }

    /**
     * An emergency stop, not a billing lapse: unlike a past-due or canceled owner, a suspended
     * owner carries no grace window, so this is checked directly against the live subscription
     * rather than through [requireRequestMutation], which a request already holding a frozen
     * execution grant otherwise never rechecks again.
     */
    fun requireNotOperationallySuspended(exchange: Exchange)
    {
        val subscription = subscriptionAccessService.resolve(owner(exchange))
        if (subscription.status == SubscriptionStatus.SUSPENDED)
        {
            throw SubscriptionDenialException(SubscriptionDenialFactory.mutationsNotAllowed(subscription))
        }
    }

    private fun owner(exchange: Exchange): SubscriptionContext
    {
        exchange.ownerOrganizationId?.let { return SubscriptionContext.forOrganization(it) }
        exchange.ownerUserId?.let { return SubscriptionContext.forUser(it) }
        throw IllegalStateException("Exchange ${exchange.id} has no owner to answer for its Information Requests")
    }
}
