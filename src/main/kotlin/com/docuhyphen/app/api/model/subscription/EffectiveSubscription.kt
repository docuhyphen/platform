package com.docuhyphen.app.api.model.subscription

import com.docuhyphen.app.api.service.subscription.BillingFrequency
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import java.time.Instant
import java.util.*

data class EffectiveSubscription(
    val planCode: PlanCode,
    val ownerType: SubscriptionOwnerType,
    val ownerId: UUID,
    val status: SubscriptionStatus,
    val features: Set<PlanFeature>,
    val limits: PlanLimits,
    val billingFrequency: BillingFrequency?,
    val currentPeriodStart: Instant?,
    val currentPeriodEnd: Instant?,
    val gracePeriodEnd: Instant?,
    val purchasedSeats: Long?,
    val upgradePlanCode: PlanCode?,
)
{
    fun hasFeature(feature: PlanFeature): Boolean
    {
        return features.contains(feature)
    }

    fun allowsMutations(at: Instant = Instant.now()): Boolean
    {
        if (status == SubscriptionStatus.TRIALING)
        {
            val trialEnd = currentPeriodEnd ?: return false
            return at.isBefore(trialEnd)
        }

        if (status == SubscriptionStatus.ACTIVE)
        {
            return true
        }

        if (status.permitsMutationsWhileInGracePeriod)
        {
            val graceEnd = gracePeriodEnd ?: return false
            return at.isBefore(graceEnd)
        }

        if (status == SubscriptionStatus.CANCELED)
        {
            val paidThrough = currentPeriodEnd ?: return false
            return at.isBefore(paidThrough)
        }

        return false
    }

    fun effectiveSeatCapacity(): Long?
    {
        return if (limits.seatsArePurchased) purchasedSeats else limits.includedSeats
    }
}

