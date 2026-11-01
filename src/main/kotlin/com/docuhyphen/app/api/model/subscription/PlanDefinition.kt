package com.docuhyphen.app.api.model.subscription

import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType

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

