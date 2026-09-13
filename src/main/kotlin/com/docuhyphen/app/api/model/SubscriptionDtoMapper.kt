package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.EffectiveSubscriptionDto
import com.docuhyphen.app.api.model.dto.SubscriptionDenialDto
import com.docuhyphen.app.api.model.dto.SubscriptionLimitsDto
import com.docuhyphen.app.api.model.dto.SubscriptionUsageDto
import com.docuhyphen.app.api.service.subscription.*
import java.time.Instant

/**
 * Maps resolved subscription values onto their transport contracts.
 *
 * External billing references and change reasons are deliberately not carried across: they are
 * operational fields for platform administration, not part of what a session needs to know.
 */
object SubscriptionDtoMapper
{
    fun toDto(
        subscription: EffectiveSubscription,
        usage: SubscriptionUsage,
        enforcementMode: SubscriptionEnforcementMode,
        availableFeatures: Set<PlanFeature> = subscription.features,
        at: Instant = Instant.now(),
    ): EffectiveSubscriptionDto
    {
        return EffectiveSubscriptionDto(
            planCode = subscription.planCode.name,
            ownerType = subscription.ownerType.name,
            ownerId = subscription.ownerId,
            status = subscription.status.name,
            features = availableFeatures.map { it.name }.sorted(),
            limits = toDto(subscription),
            usage = toDto(usage),
            allowsMutations = subscription.allowsMutations(at),
            billingFrequency = subscription.billingFrequency?.name,
            currentPeriodStart = subscription.currentPeriodStart?.toString(),
            currentPeriodEnd = subscription.currentPeriodEnd?.toString(),
            gracePeriodEnd = subscription.gracePeriodEnd?.toString(),
            upgradePlanCode = subscription.upgradePlanCode?.name,
            enforcementMode = enforcementMode.name,
        )
    }

    fun toDto(subscription: EffectiveSubscription): SubscriptionLimitsDto
    {
        return SubscriptionLimitsDto(
            maxNewExchangesPerCalendarMonth = subscription.limits.maxNewExchangesPerCalendarMonth,
            maxOpenExchanges = subscription.limits.maxOpenExchanges,
            maxAdditionalParticipantsPerExchange = subscription.limits.maxAdditionalParticipantsPerExchange,
            seatCapacity = subscription.effectiveSeatCapacity(),
            seatsArePurchased = subscription.limits.seatsArePurchased,
        )
    }

    fun toDto(usage: SubscriptionUsage): SubscriptionUsageDto
    {
        return SubscriptionUsageDto(
            newExchangesThisPeriod = usage.newExchangesThisPeriod,
            openExchanges = usage.openExchanges,
            activeSeats = usage.activeSeats,
            usagePeriodStart = usage.usagePeriodStart?.toString(),
            usagePeriodEnd = usage.usagePeriodEnd?.toString(),
        )
    }

    fun toDto(denial: SubscriptionDenial): SubscriptionDenialDto
    {
        return SubscriptionDenialDto(
            errorMessage = denial.message,
            reasonCode = denial.reason.name,
            planCode = denial.planCode.name,
            featureCode = denial.feature?.name,
            currentValue = denial.currentValue,
            limit = denial.limit,
            upgradePlanCode = denial.upgradePlanCode?.name,
        )
    }
}

