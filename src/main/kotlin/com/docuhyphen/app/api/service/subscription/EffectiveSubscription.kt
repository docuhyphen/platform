package com.docuhyphen.app.api.service.subscription

import java.time.Instant
import java.util.UUID

/**
 * The fully resolved commercial position of one paying subject: which plan they hold, the
 * features that plan grants after platform overrides are applied, the allowances that apply,
 * and the lifecycle state that decides whether mutations are still permitted.
 */
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

    /**
     * Whether the owner may still create or change resources. Past-due owners keep working
     * until their recorded grace period elapses; suspended and canceled owners keep read and
     * export access but lose write access.
     */
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

    /** Seat capacity in force: purchased quantity for Business, plan-included seats otherwise. */
    fun effectiveSeatCapacity(): Long?
    {
        return if (limits.seatsArePurchased) purchasedSeats else limits.includedSeats
    }
}

