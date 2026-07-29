package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlatformOrganizationFeatureEntitlementDto(
    val featureCode: String,
    val enabled: Boolean,
)

@Serializable
data class PlatformOrganizationSummaryDto(
    val organizationId: String,
    val name: String,
    val registrationNumber: String,
    val active: Boolean,
    val verificationComplete: Boolean,
    val createdDate: String,
    val tierCode: String,
    val maxUsers: Long? = null,
    val activeUsers: Long,
    val featureEntitlements: List<PlatformOrganizationFeatureEntitlementDto>,
)

@Serializable
data class PlatformOrganizationListDto(
    val total: Long,
    val limit: Int,
    val offset: Int,
    val items: List<PlatformOrganizationSummaryDto>,
)

@Serializable
data class PlatformOrganizationStatusUpdateRequest(
    val active: Boolean,
    val verificationComplete: Boolean,
    val changeReason: String? = null,
)

@Serializable
data class PlatformOrganizationStatusDto(
    val organizationId: String,
    val active: Boolean,
    val verificationComplete: Boolean,
)

@Serializable
data class PlatformOrganizationFeatureEntitlementUpdateDto(
    val featureCode: String,
    val enabled: Boolean,
)

@Serializable
data class PlatformOrganizationFeatureEntitlementsUpdateRequest(
    val entitlements: List<PlatformOrganizationFeatureEntitlementUpdateDto>,
    val changeReason: String? = null,
)

@Serializable
data class PlatformOrganizationFeatureEntitlementsDto(
    val organizationId: String,
    val entitlements: List<PlatformOrganizationFeatureEntitlementDto>,
)
