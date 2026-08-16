package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionTrialRequestCreateRequest(
    val note: String? = null,
)

@Serializable
data class SubscriptionTrialRequestDecisionRequest(
    val status: String,
    val durationDays: Int? = null,
    val seatCapacity: Long? = null,
    val reason: String,
)

@Serializable
data class SubscriptionTrialRequestDto(
    val id: String,
    val ownerType: String,
    val ownerId: String,
    val ownerName: String,
    val requestedByAppUserId: String,
    val requesterName: String,
    val requesterEmail: String,
    val planCode: String,
    val status: String,
    val requestNote: String? = null,
    val requestedAt: String,
    val reviewedByAppUserId: String? = null,
    val reviewedAt: String? = null,
    val decisionReason: String? = null,
    val trialGrantId: String? = null,
)

@Serializable
data class CurrentSubscriptionTrialRequestDto(
    val eligible: Boolean,
    val ineligibilityReason: String? = null,
    val request: SubscriptionTrialRequestDto? = null,
)

@Serializable
data class PlatformSubscriptionTrialRequestListDto(
    val total: Long,
    val limit: Int,
    val offset: Int,
    val items: List<SubscriptionTrialRequestDto>,
)
