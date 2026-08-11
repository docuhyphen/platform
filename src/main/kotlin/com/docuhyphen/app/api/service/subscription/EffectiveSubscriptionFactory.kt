package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Turns a persisted subscription record into the resolved commercial position used by every
 * plan decision.
 *
 * Fixed plan content comes from [PlanCatalog]. Everything tenant-specific - lifecycle status,
 * billing period, purchased seats, and platform-administered feature overrides - comes from the
 * persisted record. A record holding a plan that cannot belong to its owner falls back to that
 * owner's default plan so a corrupted value degrades predictably instead of granting or
 * removing arbitrary features.
 */
object EffectiveSubscriptionFactory
{
    private val logger = LoggerFactory.getLogger(EffectiveSubscriptionFactory::class.java)

    fun fromUserPolicy(policy: UserSubscriptionPolicy): EffectiveSubscription
    {
        val planCode = resolvePlanCode(
            persistedValue = policy.planCode,
            ownerType = SubscriptionOwnerType.USER,
            ownerId = policy.appUserId,
        )
        val definition = PlanCatalog.definitionOf(planCode)

        return EffectiveSubscription(
            planCode = planCode,
            ownerType = SubscriptionOwnerType.USER,
            ownerId = policy.appUserId,
            status = resolveStatus(policy.subscriptionStatus, policy.appUserId),
            features = definition.features,
            limits = definition.limits,
            billingFrequency = BillingFrequency.fromCodeOrNull(policy.billingFrequency),
            currentPeriodStart = policy.currentPeriodStart?.toInstant(),
            currentPeriodEnd = policy.currentPeriodEnd?.toInstant(),
            gracePeriodEnd = policy.gracePeriodEnd?.toInstant(),
            purchasedSeats = definition.limits.includedSeats,
            upgradePlanCode = definition.upgradePlanCode,
        )
    }

    /**
     * @param featureOverrides platform-administered decisions applied on top of the plan
     * defaults. An entry set to true adds a feature the plan omits; false removes one it grants.
     */
    fun fromOrganizationPolicy(
        organizationId: UUID,
        policy: OrganizationSubscriptionPolicy,
        featureOverrides: Map<PlanFeature, Boolean>,
    ): EffectiveSubscription
    {
        val planCode = resolvePlanCode(
            persistedValue = policy.tierCode,
            ownerType = SubscriptionOwnerType.ORGANIZATION,
            ownerId = organizationId,
        )
        val definition = PlanCatalog.definitionOf(planCode)

        return EffectiveSubscription(
            planCode = planCode,
            ownerType = SubscriptionOwnerType.ORGANIZATION,
            ownerId = organizationId,
            status = resolveStatus(policy.subscriptionStatus, organizationId),
            features = applyOverrides(definition.features, featureOverrides),
            limits = definition.limits,
            billingFrequency = BillingFrequency.fromCodeOrNull(policy.billingFrequency),
            currentPeriodStart = policy.currentPeriodStart?.toInstant(),
            currentPeriodEnd = policy.currentPeriodEnd?.toInstant(),
            gracePeriodEnd = policy.gracePeriodEnd?.toInstant(),
            purchasedSeats = policy.maxUsers,
            upgradePlanCode = definition.upgradePlanCode,
        )
    }

    private fun applyOverrides(
        planFeatures: Set<PlanFeature>,
        overrides: Map<PlanFeature, Boolean>,
    ): Set<PlanFeature>
    {
        if (overrides.isEmpty())
        {
            return planFeatures
        }

        val resolved = planFeatures.toMutableSet()
        overrides.forEach { (feature, enabled) ->
            if (enabled)
            {
                resolved += feature
            }
            else
            {
                resolved -= feature
            }
        }
        return resolved
    }

    private fun resolvePlanCode(
        persistedValue: String?,
        ownerType: SubscriptionOwnerType,
        ownerId: UUID,
    ): PlanCode
    {
        val fallback = when (ownerType)
        {
            SubscriptionOwnerType.USER -> PlanCatalog.DEFAULT_USER_PLAN
            SubscriptionOwnerType.ORGANIZATION -> PlanCatalog.DEFAULT_ORGANIZATION_PLAN
        }

        val parsed = PlanCode.fromCodeOrNull(persistedValue)
        if (parsed == null)
        {
            logger.warn(
                "Subscription for {} {} holds unknown plan '{}'; resolving as {}",
                ownerType,
                ownerId,
                persistedValue,
                fallback,
            )
            return fallback
        }

        if (!PlanCatalog.isAssignableTo(parsed, ownerType))
        {
            logger.warn(
                "Subscription for {} {} holds plan {} which cannot belong to that owner; resolving as {}",
                ownerType,
                ownerId,
                parsed,
                fallback,
            )
            return fallback
        }

        return parsed
    }

    private fun resolveStatus(persistedValue: String?, ownerId: UUID): SubscriptionStatus
    {
        val parsed = SubscriptionStatus.fromCodeOrNull(persistedValue)
        if (parsed == null)
        {
            logger.warn(
                "Subscription for owner {} holds unknown status '{}'; resolving as {}",
                ownerId,
                persistedValue,
                SubscriptionStatus.ACTIVE,
            )
            return SubscriptionStatus.ACTIVE
        }
        return parsed
    }
}



