package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.SubscriptionFeatureEntitlement
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementDto
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementsResponse
import com.docuhyphen.app.api.service.subscription.UserFeatureEntitlementResult
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PlatformUserFeatureEntitlementDtoMapper
{
    fun toDto(result: UserFeatureEntitlementResult): PlatformUserFeatureEntitlementsResponse
    {
        val person = result.user.person
        val displayName = listOfNotNull(person?.firstName, person?.lastName)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
        return PlatformUserFeatureEntitlementsResponse(
            appUserId = result.user.id.toString(),
            email = result.user.email,
            displayName = displayName,
            entitlements = result.entitlements.map(::toEntitlement),
        )
    }

    private fun toEntitlement(
        entitlement: SubscriptionFeatureEntitlement,
    ): PlatformUserFeatureEntitlementDto =
        PlatformUserFeatureEntitlementDto(
            featureCode = entitlement.featureCode,
            enabled = entitlement.isEnabled,
        )
}
