package com.docuhyphen.app.api.model.subscription

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.SubscriptionFeatureEntitlement

data class FeatureEntitlementDecision(
    val featureCode: String,
    val enabled: Boolean,
)

data class FeatureEntitlementReplacement(
    val beforeSnapshot: String,
    val afterSnapshot: String,
    val entitlements: List<SubscriptionFeatureEntitlement>,
)

data class UserFeatureEntitlementResult(
    val user: AppUser,
    val entitlements: List<SubscriptionFeatureEntitlement>,
)
