package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.resource.model.PlatformUserSubscriptionPolicyResponse
import com.docuhyphen.app.api.service.subscription.UserSubscriptionPolicyResult
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PlatformUserSubscriptionDtoMapper
{
    fun toDto(result: UserSubscriptionPolicyResult): PlatformUserSubscriptionPolicyResponse
    {
        val person = result.user.person
        val displayName = listOfNotNull(person?.firstName, person?.lastName)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
        val policy = result.policy
        return PlatformUserSubscriptionPolicyResponse(
            appUserId = result.user.id.toString(),
            email = result.user.email,
            displayName = displayName,
            planCode = policy.planCode,
            subscriptionStatus = policy.subscriptionStatus,
            billingFrequency = policy.billingFrequency,
            currentPeriodStart = policy.currentPeriodStart?.toInstant()?.toString(),
            currentPeriodEnd = policy.currentPeriodEnd?.toInstant()?.toString(),
            gracePeriodEnd = policy.gracePeriodEnd?.toInstant()?.toString(),
            changeReason = policy.changeReason,
            createdDate = policy.createdDate.toInstant().toString(),
            updatedDate = policy.updatedDate.toInstant().toString(),
        )
    }
}
