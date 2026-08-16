package com.docuhyphen.app.api.service.subscription

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class SubscriptionTrialRequestEligibilityService @Inject constructor(
    private val subscriptionPolicyService: SubscriptionPolicyService,
)
{
    fun evaluateForRequest(ownerType: SubscriptionOwnerType, ownerId: UUID): SubscriptionTrialRequestEligibility
    {
        return when (ownerType)
        {
            SubscriptionOwnerType.USER ->
            {
                subscriptionPolicyService.ensureUserPolicy(ownerId)
                val policy = subscriptionPolicyService.findUserPolicyForUpdate(ownerId)
                    ?: throw IllegalStateException("User subscription policy could not be locked")
                SubscriptionTrialEligibilityPolicy.evaluate(
                    PlanCode.fromCode(policy.planCode),
                    SubscriptionStatus.fromCode(policy.subscriptionStatus),
                    BillingFrequency.fromCodeOrNull(policy.billingFrequency),
                    policy.currentPeriodEnd?.toInstant(),
                    policy.externalBillingCustomerRef,
                    policy.externalBillingSubscriptionRef,
                )
            }
            SubscriptionOwnerType.ORGANIZATION ->
            {
                subscriptionPolicyService.ensureOrganizationPolicy(ownerId)
                val policy = subscriptionPolicyService.findOrganizationPolicyForUpdate(ownerId)
                    ?: throw IllegalStateException("Organization subscription policy could not be locked")
                SubscriptionTrialEligibilityPolicy.evaluate(
                    PlanCode.fromCode(policy.tierCode),
                    SubscriptionStatus.fromCode(policy.subscriptionStatus),
                    BillingFrequency.fromCodeOrNull(policy.billingFrequency),
                    policy.currentPeriodEnd?.toInstant(),
                    policy.externalBillingCustomerRef,
                    policy.externalBillingSubscriptionRef,
                )
            }
        }
    }
}
