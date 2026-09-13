package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

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
 *
 * A capability still under controlled release is the one exception. It answers to a second,
 * independent decision as well, held in deployment configuration rather than against the owner, and
 * neither decision is softened by the enforcement mode.
 */
@ApplicationScoped
class SubscriptionAccessService @Inject constructor(
    private val subscriptionPolicyService: SubscriptionPolicyService,
    private val subscriptionUsageService: SubscriptionUsageService,
    private val enforcementConfigService: SubscriptionEnforcementConfigService,
    private val featureRolloutConfigService: FeatureRolloutConfigService,
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

    /**
     * Whether the owner can reach the feature, answered without refusing anything. Callers that
     * decide what to offer rather than what to permit use this; callers that are about to act use
     * [requireFeature] so the refusal reaches the caller with its reason.
     */
    fun isFeatureAvailable(context: SubscriptionContext, feature: PlanFeature): Boolean
    {
        return isReleasedTo(context, feature) && resolve(context).hasFeature(feature)
    }

    /**
     * The features the owner can actually reach, which is a narrower answer than the features
     * their plan and overrides resolve to. A capability under controlled release is entitled but
     * unreachable until this deployment turns it on for that owner, and a contract that advertised
     * it would promise something every call site then refuses.
     */
    fun availableFeatures(subscription: EffectiveSubscription): Set<PlanFeature>
    {
        val context = SubscriptionContext(subscription.ownerType, subscription.ownerId)
        return subscription.features.filterTo(mutableSetOf()) { isReleasedTo(context, it) }
    }

    private fun isReleasedTo(context: SubscriptionContext, feature: PlanFeature): Boolean
    {
        return !feature.requiresRolloutGrant || featureRolloutConfigService.isGranted(context, feature)
    }

    fun requireFeature(context: SubscriptionContext, feature: PlanFeature)
    {
        if (feature.requiresRolloutGrant)
        {
            requireReleasedFeature(context, feature)
            return
        }

        if (!enforcementMode().evaluatesDecisions)
        {
            return
        }

        val subscription = resolve(context)
        if (subscription.hasFeature(feature))
        {
            return
        }

        // Pointing an individual at an organization plan is only true advice when an organization
        // plan actually sells the feature. A feature no plan sells is refused on its own terms.
        val soldOnlyToOrganizations =
            !PlanCatalog.definitionOf(PlanCode.PERSONAL).includes(feature) &&
                    PlanCatalog.definitionOf(PlanCode.BUSINESS).includes(feature)

        val denial = if (subscription.ownerType == SubscriptionOwnerType.USER && soldOnlyToOrganizations)
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

    /**
     * Applies both decisions that govern a capability under controlled release: the commercial
     * grant recorded against the owner, and this deployment having turned the capability on for
     * that same owner. Holding one without the other is refused.
     *
     * Neither half observes the configured enforcement mode. That mode exists so an environment can
     * watch what commercial policy would refuse before customers are blocked by it, and an
     * unfinished capability must not become reachable by relaxing an unrelated commercial setting.
     */
    private fun requireReleasedFeature(context: SubscriptionContext, feature: PlanFeature)
    {
        val subscription = resolve(context)

        if (!subscription.hasFeature(feature))
        {
            refuse(SubscriptionDenialFactory.featureNotIncluded(subscription, feature))
        }

        if (!featureRolloutConfigService.isGranted(context, feature))
        {
            refuse(SubscriptionDenialFactory.featureNotReleased(subscription, feature))
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
            refuse(denial)
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

    /** Records the refusal and raises it, for a decision that no enforcement mode softens. */
    private fun refuse(denial: SubscriptionDenial): Nothing
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

    private fun denialType(denial: SubscriptionDenial): String = when (denial.reason)
    {
        SubscriptionDenialReason.PLAN_LIMIT_REACHED -> "USAGE_LIMIT"
        SubscriptionDenialReason.SEAT_LIMIT_REACHED -> "SEAT_LIMIT"
        SubscriptionDenialReason.FEATURE_NOT_RELEASED -> "ROLLOUT"
        SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
        SubscriptionDenialReason.ORGANIZATION_SUBSCRIPTION_REQUIRED,
        -> "FEATURE"
        else -> "LIFECYCLE"
    }

    private fun resolveUser(appUserId: UUID): EffectiveSubscription
    {
        val policy = subscriptionPolicyService.findUserPolicy(appUserId)
            ?: subscriptionPolicyService.ensureUserPolicy(appUserId)
        val overrides = subscriptionPolicyService.featureOverrides(
            SubscriptionContext.forUser(appUserId),
        )
        return EffectiveSubscriptionFactory.fromUserPolicy(policy, overrides)
    }

    private fun resolveOrganization(organizationId: UUID): EffectiveSubscription
    {
        val policy = subscriptionPolicyService.findOrganizationPolicy(organizationId)
            ?: subscriptionPolicyService.ensureOrganizationPolicy(organizationId)
        val overrides = subscriptionPolicyService.featureOverrides(
            SubscriptionContext.forOrganization(organizationId),
        )
        return EffectiveSubscriptionFactory.fromOrganizationPolicy(organizationId, policy, overrides)
    }
}

