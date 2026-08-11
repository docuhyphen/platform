package com.docuhyphen.app.api.service.subscription

/**
 * Immutable definition of a single plan: which principal may own it, which product features it
 * includes, its allowances, and the plan a blocked owner should be pointed at.
 */
data class PlanDefinition(
    val planCode: PlanCode,
    val ownerType: SubscriptionOwnerType,
    val features: Set<PlanFeature>,
    val limits: PlanLimits,
    val upgradePlanCode: PlanCode?,
)
{
    fun includes(feature: PlanFeature): Boolean
    {
        return features.contains(feature)
    }
}

