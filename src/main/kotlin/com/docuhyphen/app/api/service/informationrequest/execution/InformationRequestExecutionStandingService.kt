package com.docuhyphen.app.api.service.informationrequest.execution

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestOwnerStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestStandingReason
import com.docuhyphen.app.api.service.subscription.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant

@ApplicationScoped
class InformationRequestExecutionStandingService @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
    private val executionGrantService: InformationRequestExecutionGrantService,
)
{
    fun ownerStanding(exchange: Exchange): InformationRequestOwnerStanding = ownerStanding(ownerOf(exchange))

    fun ownerStanding(request: InformationRequest): InformationRequestOwnerStanding = ownerStanding(ownerOf(request))

    fun ownerStanding(owner: SubscriptionContext): InformationRequestOwnerStanding
    {
        val subscription = subscriptionAccessService.resolve(owner)
        val mode = subscriptionAccessService.enforcementMode()
        val suspended = subscription.status == SubscriptionStatus.SUSPENDED
        return InformationRequestOwnerStanding(
            ownerType = subscription.ownerType,
            ownerId = subscription.ownerId,
            planCode = subscription.planCode,
            status = subscription.status,
            enforcementMode = mode,
            featureIncluded = subscription.hasFeature(PlanFeature.INFORMATION_REQUESTS),
            operationallySuspended = suspended,
            newWorkUnavailableReason = newWorkUnavailableReason(subscription, mode, suspended, Instant.now()),
        )
    }

    fun standingOf(
        request: InformationRequest,
        owner: InformationRequestOwnerStanding
    ): InformationRequestExecutionStanding
    {
        val grant = executionGrantService.findForRequest(request.id)
        val lapse = owner.newWorkUnavailableReason
        return when
        {
            owner.operationallySuspended -> InformationRequestExecutionStanding(
                InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED,
                InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED,
            )

            grant?.revokedAt != null -> InformationRequestExecutionStanding(
                InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED,
                InformationRequestStandingReason.EXECUTION_GRANT_REVOKED,
            )

            lapse == null -> InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE)
            grant == null -> InformationRequestExecutionStanding(
                InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE,
                lapse
            )

            else -> InformationRequestExecutionStanding(
                InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE,
                lapse
            )
        }
    }

    private fun newWorkUnavailableReason(
        subscription: EffectiveSubscription,
        mode: SubscriptionEnforcementMode,
        suspended: Boolean,
        at: Instant,
    ): InformationRequestStandingReason? = when
    {
        suspended -> InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED
        !mode.refusesDeniedRequests -> null
        !subscription.allowsMutations(at) -> when (subscription.status)
        {
            SubscriptionStatus.TRIALING -> InformationRequestStandingReason.TRIAL_ENDED
            SubscriptionStatus.PAST_DUE -> InformationRequestStandingReason.SUBSCRIPTION_PAST_DUE
            SubscriptionStatus.CANCELED -> InformationRequestStandingReason.SUBSCRIPTION_CANCELED
            else -> InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED
        }

        !subscription.hasFeature(PlanFeature.INFORMATION_REQUESTS) -> InformationRequestStandingReason.FEATURE_NOT_INCLUDED
        else -> null
    }

    private fun ownerOf(request: InformationRequest): SubscriptionContext
    {
        request.ownerOrganizationId?.let { return SubscriptionContext.forOrganization(it) }
        request.ownerUserId?.let { return SubscriptionContext.forUser(it) }
        throw IllegalStateException("Information Request ${request.id} has no owner")
    }

    private fun ownerOf(exchange: Exchange): SubscriptionContext
    {
        exchange.ownerOrganizationId?.let { return SubscriptionContext.forOrganization(it) }
        exchange.ownerUserId?.let { return SubscriptionContext.forUser(it) }
        throw IllegalStateException("Exchange ${exchange.id} has no owner to answer for its Information Requests")
    }
}
