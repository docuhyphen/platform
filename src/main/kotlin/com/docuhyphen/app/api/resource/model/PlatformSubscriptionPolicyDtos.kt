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

@Serializable
data class PlatformSubscriptionTrialStartRequest(
    val planCode: String,
    val durationDays: Int? = null,
    val seatCapacity: Long? = null,
    val reason: String,
)

@Serializable
data class PlatformSubscriptionTrialExtensionRequest(
    val currentPeriodEnd: String,
    val reason: String,
)

@Serializable
data class PlatformSubscriptionTrialEndRequest(
    val reason: String,
)

@Serializable
data class PlatformSubscriptionTrialConversionRequest(
    val billingFrequency: String,
    val currentPeriodEnd: String,
    val seatCapacity: Long? = null,
    val reason: String,
)

@Serializable
data class PlatformSubscriptionTrialResponse(
    val ownerType: String,
    val ownerId: String,
    val planCode: String,
    val subscriptionStatus: String,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
    val seatCapacity: Long? = null,
    val grantId: String,
    val grantSource: String,
    val reason: String,
)

@Serializable
data class PlatformSubscriptionTrialTransitionResponse(
    val ownerType: String,
    val ownerId: String,
    val planCode: String,
    val subscriptionStatus: String,
    val billingFrequency: String? = null,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
    val seatCapacity: Long? = null,
    val reason: String,
)

@Serializable
data class PlatformUserFeatureEntitlementDto(
    val featureCode: String,
    val enabled: Boolean,
)

@Serializable
data class PlatformUserFeatureEntitlementsUpdateRequest(
    val entitlements: List<PlatformUserFeatureEntitlementDto>,
    val changeReason: String? = null,
)

@Serializable
data class PlatformUserFeatureEntitlementsResponse(
    val appUserId: String,
    val email: String,
    val displayName: String? = null,
    val entitlements: List<PlatformUserFeatureEntitlementDto>,
)
