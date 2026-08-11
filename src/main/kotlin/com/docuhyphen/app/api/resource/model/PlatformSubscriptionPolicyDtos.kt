package com.docuhyphen.app.api.resource.model

import kotlinx.serialization.Serializable

@Serializable
data class PlatformOrganizationSubscriptionPolicyRequest(
    val tierCode: String,
    val maxUsers: Long? = null,
    val subscriptionStatus: String = "ACTIVE",
    val billingFrequency: String? = null,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
    val gracePeriodEnd: String? = null,
    val changeReason: String = "",
)

@Serializable
data class PlatformOrganizationSubscriptionPolicyResponse(
    val organizationId: String,
    val tierCode: String,
    val maxUsers: Long? = null,
    val currentActiveUsers: Long,
    val subscriptionStatus: String,
    val billingFrequency: String? = null,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
    val gracePeriodEnd: String? = null,
    val changeReason: String? = null,
    val persisted: Boolean,
    val createdDate: String? = null,
    val updatedDate: String? = null,
)

@Serializable
data class PlatformOrganizationSubscriptionPolicyListResponse(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val items: List<PlatformOrganizationSubscriptionPolicyResponse>,
)

@Serializable
data class PlatformUserSubscriptionPolicyRequest(
    val planCode: String,
    val subscriptionStatus: String,
    val billingFrequency: String? = null,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
    val gracePeriodEnd: String? = null,
    val changeReason: String,
)

@Serializable
data class PlatformUserSubscriptionPolicyResponse(
    val appUserId: String,
    val email: String,
    val displayName: String? = null,
    val planCode: String,
    val subscriptionStatus: String,
    val billingFrequency: String? = null,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
    val gracePeriodEnd: String? = null,
    val changeReason: String? = null,
    val createdDate: String,
    val updatedDate: String,
)

@Serializable
data class PlatformUserSubscriptionPolicyListResponse(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val items: List<PlatformUserSubscriptionPolicyResponse>,
)
