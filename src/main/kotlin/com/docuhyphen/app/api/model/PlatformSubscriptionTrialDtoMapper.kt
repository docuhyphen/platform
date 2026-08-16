package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialResponse
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialTransitionResponse
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialMutation
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialTransition
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PlatformSubscriptionTrialDtoMapper
{
    fun toDto(mutation: SubscriptionTrialMutation): PlatformSubscriptionTrialResponse
    {
        return PlatformSubscriptionTrialResponse(
            ownerType = mutation.ownerType.name,
            ownerId = mutation.ownerId.toString(),
            planCode = mutation.after.planCode.name,
            subscriptionStatus = mutation.after.status.name,
            currentPeriodStart = mutation.after.currentPeriodStart?.toString(),
            currentPeriodEnd = mutation.after.currentPeriodEnd?.toString(),
            seatCapacity = mutation.after.seatCapacity,
            grantId = mutation.grant.id.toString(),
            grantSource = mutation.grant.source,
            reason = mutation.grant.reason,
        )
    }

    fun toDto(transition: SubscriptionTrialTransition): PlatformSubscriptionTrialTransitionResponse
    {
        return PlatformSubscriptionTrialTransitionResponse(
            ownerType = transition.ownerType.name,
            ownerId = transition.ownerId.toString(),
            planCode = transition.after.planCode.name,
            subscriptionStatus = transition.after.status.name,
            billingFrequency = transition.after.billingFrequency?.name,
            currentPeriodStart = transition.after.currentPeriodStart?.toString(),
            currentPeriodEnd = transition.after.currentPeriodEnd?.toString(),
            seatCapacity = transition.after.seatCapacity,
            reason = transition.reason,
        )
    }
}
