package com.docuhyphen.app.api.service.subscription

/**
 * A refusal produced by a commercial plan check, described in full so the same value can be
 * logged in report-only environments and returned to the caller in enforcing environments.
 *
 * [currentValue] and [limit] are populated only for allowance refusals so the caller can be
 * told exactly how much of the allowance is in use.
 */
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

