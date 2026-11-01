package com.docuhyphen.app.api.model.subscription

import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType

data class SubscriptionDenial(
    val reason: SubscriptionDenialReason,
    val planCode: PlanCode,
    val ownerType: SubscriptionOwnerType,
    val feature: PlanFeature? = null,
    val currentValue: Long? = null,
    val limit: Long? = null,
    val upgradePlanCode: PlanCode? = null,
    val message: String,
)

