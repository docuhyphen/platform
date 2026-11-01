package com.docuhyphen.app.api.model.subscription

import com.docuhyphen.app.api.service.subscription.BillingFrequency
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
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

