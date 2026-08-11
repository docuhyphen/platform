package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * The single authority for commercial plan decisions.
 *
 * Resolution answers "which paying subject owns this operation, what does their plan include,
 * and what allowance is left". It never answers "is this caller authorized", which stays with
 * role capabilities and Share grants. A protected operation must satisfy both: holding a role
 * grants no plan feature, and owning a plan grants no role permission.
 *
 * Refusals are produced identically in every environment. The configured enforcement mode only
 * decides whether a refusal is recorded and allowed through or raised to the caller, so a new
 * environment can be observed before anything is actually blocked.
 */
@ApplicationScoped
class SubscriptionAccessService @Inject constructor(
    private val subscriptionPolicyService: SubscriptionPolicyService,
    private val subscriptionUsageService: SubscriptionUsageService,
    private val enforcementConfigService: SubscriptionEnforcementConfigService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SubscriptionAccessService::class.java)
    }

    fun enforcementMode(): SubscriptionEnforcementMode = enforcementConfigService.mode()

    /**
     * Resolves the commercial position of the paying subject. The record is created on demand
     * when a subject somehow has none, so no call site has to cope with a missing subscription.
     */
    fun resolve(context: SubscriptionContext): EffectiveSubscription
    {
        return try
        {
            when (context.ownerType)
            {
                SubscriptionOwnerType.USER -> resolveUser(context.ownerId)
                SubscriptionOwnerType.ORGANIZATION -> resolveOrganization(context.ownerId)
            }
        }
        catch (exception: Exception)
        {
            logger.error(
                "event=subscription_resolution_failure ownerType={} ownerId={} failureType={}",
                context.ownerType,
                context.ownerId,
                exception.javaClass.simpleName,
                exception,
            )
            throw exception
        }
    }

    /**
     * Resolves the subject for a session: the selected organization when one is active,
     * otherwise the authenticated user acting personally.
     */
    fun resolveForSession(appUserId: UUID, activeOrganizationId: UUID?): EffectiveSubscription
    {
        val context = activeOrganizationId
            ?.let { SubscriptionContext.forOrganization(it) }
            ?: SubscriptionContext.forUser(appUserId)
        return resolve(context)
    }

    fun measureUsage(
        subscription: EffectiveSubscription,
        at: Instant = Instant.now(),
    ): SubscriptionUsage
    {
        return subscriptionUsageService.measure(subscription, at)
    }

    fun requireFeature(context: SubscriptionContext, feature: PlanFeature)
    {
        if (!enforcementMode().evaluatesDecisions)
        {
            return
        }

        val subscription = resolve(context)
        if (subscription.hasFeature(feature))
        {
            return
        }

        val denial = if (
            subscription.ownerType == SubscriptionOwnerType.USER &&
            !PlanCatalog.definitionOf(PlanCode.PERSONAL).includes(feature)
        )
        {
            SubscriptionDenialFactory.organizationSubscriptionRequired(feature)
        }
        else
        {
            SubscriptionDenialFactory.featureNotIncluded(subscription, feature)
        }

        apply(denial)
    }

    fun requireMutationAllowed(context: SubscriptionContext, at: Instant = Instant.now())
    {
        if (!enforcementMode().evaluatesDecisions)
        {
            return
        }

        val subscription = resolve(context)
        if (subscription.allowsMutations(at))
        {
            return
        }

        apply(SubscriptionDenialFactory.mutationsNotAllowed(subscription))
    }

    /**
     * Checks the Exchange allowances before an Exchange is created: how many may be created in
     * the current calendar month, and how many may stay open at once. Drafts count towards the
     * open allowance so it cannot be sidestepped by leaving Exchanges unsent.
     */
    fun requireExchangeCapacity(context: SubscriptionContext, at: Instant = Instant.now())
    {
        if (!enforcementMode().evaluatesDecisions)
        {
            return
        }

        val subscription = resolve(context)
        requireFeatureOn(subscription, PlanFeature.EXCHANGE_CREATE)

        val monthlyLimit = subscription.limits.maxNewExchangesPerCalendarMonth
        if (monthlyLimit != null)
        {
            val created = subscriptionUsageService.countNewExchangesThisPeriod(context, at)
            if (created >= monthlyLimit)
            {
                apply(
                    SubscriptionDenialFactory.limitReached(
                        subscription = subscription,
                        feature = PlanFeature.EXCHANGE_CREATE,
                        allowanceDescription = "$monthlyLimit new Exchanges per calendar month.",
                        currentValue = created,
                        limit = monthlyLimit,
                    ),
                )
            }
        }

        val openLimit = subscription.limits.maxOpenExchanges
        if (openLimit != null)
        {
            val open = subscriptionUsageService.countOpenExchanges(context)
            if (open >= openLimit)
            {
                apply(
                    SubscriptionDenialFactory.limitReached(
                        subscription = subscription,
                        feature = PlanFeature.EXCHANGE_CREATE,
                        allowanceDescription = "$openLimit open Exchanges at a time. " +
                            "Complete or end an Exchange to free capacity.",
                        currentValue = open,
                        limit = openLimit,
                    ),
                )
            }
        }
    }

    /**
     * Checks whether the subject may add participants beyond the required primary recipient.
     */
    fun requireParticipantCapacity(context: SubscriptionContext, additionalParticipants: Int)
    {
        if (!enforcementMode().evaluatesDecisions || additionalParticipants <= 0)
        {
            return
        }

        val subscription = resolve(context)
        if (!subscription.hasFeature(PlanFeature.MULTIPLE_PARTICIPANTS))
        {
            apply(
                SubscriptionDenialFactory.featureNotIncluded(
                    subscription,
                    PlanFeature.MULTIPLE_PARTICIPANTS,
                ),
            )
            return
        }

        val participantLimit = subscription.limits.maxAdditionalParticipantsPerExchange ?: return
        if (additionalParticipants > participantLimit)
        {
            apply(
                SubscriptionDenialFactory.limitReached(
                    subscription = subscription,
                    feature = PlanFeature.MULTIPLE_PARTICIPANTS,
                    allowanceDescription = "$participantLimit additional participants per Exchange.",
                    currentValue = additionalParticipants.toLong(),
                    limit = participantLimit,
                ),
            )
        }
    }

    /**
     * Checks that the organization has an unused purchased seat before a membership becomes
     * active. An organization with no assigned seat quantity is uncapped.
     */
    fun requireOrganizationSeat(organizationId: UUID)
    {
        if (!enforcementMode().evaluatesDecisions)
        {
            return
        }

        val subscription = resolveOrganization(organizationId)
        val capacity = subscription.effectiveSeatCapacity() ?: return
        val activeSeats = subscriptionUsageService.countActiveSeats(organizationId)

        if (activeSeats >= capacity)
        {
            apply(SubscriptionDenialFactory.seatLimitReached(subscription, activeSeats, capacity))
        }
    }

    private fun requireFeatureOn(subscription: EffectiveSubscription, feature: PlanFeature)
    {
        if (!subscription.hasFeature(feature))
        {
            apply(SubscriptionDenialFactory.featureNotIncluded(subscription, feature))
        }
    }

    /**
     * Records the refusal, then raises it only when enforcement is active. Report-only
     * environments get a full picture of what would be blocked without affecting customers.
     */
    private fun apply(denial: SubscriptionDenial)
    {
        val mode = enforcementMode()
        if (mode.refusesDeniedRequests)
        {
            logger.info(
                "event=subscription_decision outcome=ENFORCED denialType={} reason={} plan={} ownerType={} feature={} current={} limit={}",
                denialType(denial),
                denial.reason,
                denial.planCode,
                denial.ownerType,
                denial.feature,
                denial.currentValue,
                denial.limit,
            )
            throw SubscriptionDenialException(denial)
        }

        logger.warn(
            "event=subscription_decision outcome=WOULD_DENY denialType={} reason={} plan={} ownerType={} feature={} current={} limit={}",
            denialType(denial),
            denial.reason,
            denial.planCode,
            denial.ownerType,
            denial.feature,
            denial.currentValue,
            denial.limit,
        )
    }

    private fun denialType(denial: SubscriptionDenial): String = when (denial.reason)
    {
        SubscriptionDenialReason.PLAN_LIMIT_REACHED -> "USAGE_LIMIT"
        SubscriptionDenialReason.SEAT_LIMIT_REACHED -> "SEAT_LIMIT"
        SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
        SubscriptionDenialReason.ORGANIZATION_SUBSCRIPTION_REQUIRED,
        -> "FEATURE"
        else -> "LIFECYCLE"
    }

    private fun resolveUser(appUserId: UUID): EffectiveSubscription
    {
        val policy = subscriptionPolicyService.findUserPolicy(appUserId)
            ?: subscriptionPolicyService.ensureUserPolicy(appUserId)
        return EffectiveSubscriptionFactory.fromUserPolicy(policy)
    }

    private fun resolveOrganization(organizationId: UUID): EffectiveSubscription
    {
        val policy = subscriptionPolicyService.findOrganizationPolicy(organizationId)
            ?: subscriptionPolicyService.ensureOrganizationPolicy(organizationId)
        val overrides = subscriptionPolicyService.organizationFeatureOverrides(organizationId)
        return EffectiveSubscriptionFactory.fromOrganizationPolicy(organizationId, policy, overrides)
    }
}

