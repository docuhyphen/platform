package com.docuhyphen.app.api.service.subscription

import java.time.Instant

object SubscriptionTrialEligibilityPolicy
{
    fun evaluate(
        planCode: PlanCode,
        status: SubscriptionStatus,
        billingFrequency: BillingFrequency?,
        currentPeriodEnd: Instant?,
        externalBillingCustomerRef: String?,
        externalBillingSubscriptionRef: String?,
        now: Instant = Instant.now(),
    ): SubscriptionTrialRequestEligibility
    {
        if (status == SubscriptionStatus.TRIALING && currentPeriodEnd?.isAfter(now) == true)
        {
            return SubscriptionTrialRequestEligibility(false, "An active trial already exists")
        }
        if (billingFrequency != null ||
            !externalBillingCustomerRef.isNullOrBlank() ||
            !externalBillingSubscriptionRef.isNullOrBlank())
        {
            return SubscriptionTrialRequestEligibility(false, "This account already has paid billing set up")
        }
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.TRIALING)
        {
            return SubscriptionTrialRequestEligibility(false, "The subscription lifecycle is not eligible for a trial")
        }
        if (planCode == PlanCode.PERSONAL && status == SubscriptionStatus.ACTIVE)
        {
            return SubscriptionTrialRequestEligibility(false, "The user already has an active Personal subscription")
        }
        return SubscriptionTrialRequestEligibility(true)
    }
}
