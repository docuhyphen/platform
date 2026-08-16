package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.PlatformOrganizationFeatureEntitlementDto
import com.docuhyphen.app.api.model.dto.PlatformOrganizationFeatureEntitlementsDto
import com.docuhyphen.app.api.model.dto.PlatformOrganizationSummaryDto
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationFeatureEntitlement
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.resource.model.PlatformOrganizationSubscriptionPolicyResponse
import com.docuhyphen.app.api.service.subscription.PlatformOrganizationSubscriptionPolicyService
import com.docuhyphen.app.api.service.subscription.PolicyResult
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class PlatformOrganizationDtoMapper
{
    fun toSummary(
        organization: Organization,
        policy: OrganizationSubscriptionPolicy?,
        activeUsers: Long,
        entitlements: List<OrganizationFeatureEntitlement>,
    ): PlatformOrganizationSummaryDto
    {
        val tierCode = policy?.tierCode ?: PlatformOrganizationSubscriptionPolicyService.ORGANIZATION_TIER_CODE
        val maxUsers = policy?.maxUsers ?: PlatformOrganizationSubscriptionPolicyService.UNASSIGNED_SEAT_CAPACITY

        return PlatformOrganizationSummaryDto(
            organizationId = organization.id.toString(),
            name = organization.name,
            registrationNumber = organization.registrationNumber,
            active = organization.isActive,
            verificationComplete = organization.verificationComplete,
            createdDate = organization.createdDate.toInstant().toString(),
            tierCode = tierCode,
            maxUsers = maxUsers,
            subscriptionStatus = policy?.subscriptionStatus ?: "ACTIVE",
            billingFrequency = policy?.billingFrequency,
            currentPeriodStart = policy?.currentPeriodStart?.toInstant()?.toString(),
            currentPeriodEnd = policy?.currentPeriodEnd?.toInstant()?.toString(),
            gracePeriodEnd = policy?.gracePeriodEnd?.toInstant()?.toString(),
            activeUsers = activeUsers,
            featureEntitlements = entitlements.map(::toEntitlement),
        )
    }

    fun toEntitlements(
        organizationId: UUID,
        entitlements: List<OrganizationFeatureEntitlement>,
    ): PlatformOrganizationFeatureEntitlementsDto =
        PlatformOrganizationFeatureEntitlementsDto(
            organizationId = organizationId.toString(),
            entitlements = entitlements.map(::toEntitlement),
        )

    fun toSubscriptionPolicy(
        result: PolicyResult,
    ): PlatformOrganizationSubscriptionPolicyResponse =
        PlatformOrganizationSubscriptionPolicyResponse(
            organizationId = result.organizationId.toString(),
            tierCode = result.tierCode,
            maxUsers = result.maxUsers,
            currentActiveUsers = result.currentActiveUsers,
            subscriptionStatus = result.subscriptionStatus,
            billingFrequency = result.billingFrequency,
            currentPeriodStart = result.currentPeriodStart?.toInstant()?.toString(),
            currentPeriodEnd = result.currentPeriodEnd?.toInstant()?.toString(),
            gracePeriodEnd = result.gracePeriodEnd?.toInstant()?.toString(),
            changeReason = result.changeReason,
            persisted = result.persisted,
            createdDate = result.createdDate?.toString(),
            updatedDate = result.updatedDate?.toString(),
        )

    private fun toEntitlement(
        entitlement: OrganizationFeatureEntitlement,
    ): PlatformOrganizationFeatureEntitlementDto =
        PlatformOrganizationFeatureEntitlementDto(
            featureCode = entitlement.featureCode,
            enabled = entitlement.isEnabled,
        )
}
