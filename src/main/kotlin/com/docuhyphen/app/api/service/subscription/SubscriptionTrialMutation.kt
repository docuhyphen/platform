package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.SubscriptionTrialGrant
import java.time.Instant
import java.util.UUID

data class SubscriptionTrialState(
    val planCode: PlanCode,
    val status: SubscriptionStatus,
    val billingFrequency: BillingFrequency?,
    val currentPeriodStart: Instant?,
    val currentPeriodEnd: Instant?,
    val seatCapacity: Long?,
)

data class SubscriptionTrialTransition(
    val ownerType: SubscriptionOwnerType,
    val ownerId: UUID,
    val before: SubscriptionTrialState,
    val after: SubscriptionTrialState,
    val reason: String,
)

data class SubscriptionTrialMutation(
    val ownerType: SubscriptionOwnerType,
    val ownerId: UUID,
    val before: SubscriptionTrialState,
    val after: SubscriptionTrialState,
    val grant: SubscriptionTrialGrant,
)

data class SubscriptionTrialPolicyMutation(
    val ownerType: SubscriptionOwnerType,
    val ownerId: UUID,
    val before: SubscriptionTrialState,
    val after: SubscriptionTrialState,
    val grantedIntervalStart: Instant,
    val grantedIntervalEnd: Instant,
)
