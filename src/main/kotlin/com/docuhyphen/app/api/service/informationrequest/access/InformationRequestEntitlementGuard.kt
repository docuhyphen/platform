package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.subscription.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestEntitlementGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
    private val requestRepository: InformationRequestRepository,
)
{
    fun requireRequestCreation(exchange: Exchange)
    {
        requireRequestMutation(exchange)
        val owner = owner(exchange)
        val ownerType = when (owner.ownerType)
        {
            SubscriptionOwnerType.ORGANIZATION -> InformationRequestOwnerType.ORGANIZATION
            SubscriptionOwnerType.USER -> InformationRequestOwnerType.USER
        }
        subscriptionAccessService.requireInformationRequestCapacity(owner) {
            requestRepository.countOpenForOwner(ownerType, owner.ownerId)
        }
    }

    fun requireRequestMutation(exchange: Exchange)
    {
        requireNotOperationallySuspended(exchange)
        val owner = owner(exchange)
        subscriptionAccessService.requireMutationAllowed(owner)
        subscriptionAccessService.requireFeature(owner, PlanFeature.INFORMATION_REQUESTS)
    }

    fun requireNotOperationallySuspended(exchange: Exchange)
    {
        val subscription = subscriptionAccessService.resolve(owner(exchange))
        if (subscription.status == SubscriptionStatus.SUSPENDED)
        {
            throw SubscriptionDenialException(SubscriptionDenialFactory.mutationsNotAllowed(subscription))
        }
    }

    fun isOperationallySuspended(exchange: Exchange): Boolean =
        subscriptionAccessService.resolve(owner(exchange)).status == SubscriptionStatus.SUSPENDED

    fun permitsRequestMutation(exchange: Exchange): Boolean =
        try {
            requireRequestMutation(exchange)
            true
        } catch (_: SubscriptionDenialException) {
            false
        }

    private fun owner(exchange: Exchange): SubscriptionContext
    {
        exchange.ownerOrganizationId?.let { return SubscriptionContext.forOrganization(it) }
        exchange.ownerUserId?.let { return SubscriptionContext.forUser(it) }
        throw IllegalStateException("Exchange ${exchange.id} has no owner to answer for its Information Requests")
    }
}
