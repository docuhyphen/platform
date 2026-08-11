package com.docuhyphen.app.api.service.subscription

import java.time.Instant

data class SubscriptionLifecycleUpdate(
    val planCode: PlanCode,
    val status: SubscriptionStatus,
    val billingFrequency: BillingFrequency?,
    val currentPeriodStart: Instant?,
    val currentPeriodEnd: Instant?,
    val gracePeriodEnd: Instant?,
    val changeReason: String,
)

