package com.docuhyphen.app.api.service.subscription

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class SubscriptionTrialTransitionService @Inject constructor(
    private val subscriptionPolicyService: SubscriptionPolicyService,
    private val lifecycleValidator: SubscriptionLifecycleValidator,
)
{
    @Transactional
    fun endUserTrial(appUserId: UUID, reason: String): SubscriptionTrialTransition
    {
        val normalizedReason = validateReason(reason)
        val policy = subscriptionPolicyService.findUserPolicyForUpdate(appUserId)
            ?: throw IllegalArgumentException("User subscription policy not found")
        val before = requireTrial(SubscriptionOwnerType.USER, state(policy))
        val now = Instant.now()
        val lifecycle = SubscriptionLifecycleUpdate(
            planCode = PlanCode.FREE,
            status = SubscriptionStatus.ACTIVE,
            billingFrequency = null,
            currentPeriodStart = null,
            currentPeriodEnd = null,
            gracePeriodEnd = null,
            changeReason = normalizedReason,
        )
        lifecycleValidator.validate(SubscriptionOwnerType.USER, lifecycle)
        policy.planCode = lifecycle.planCode.name
        policy.subscriptionStatus = lifecycle.status.name
        policy.billingFrequency = null
        policy.currentPeriodStart = null
        policy.currentPeriodEnd = null
        policy.gracePeriodEnd = null
        policy.changeReason = normalizedReason
        policy.updatedDate = Timestamp.from(now)
        subscriptionPolicyService.updateUserPolicy(policy)
        return transition(SubscriptionOwnerType.USER, appUserId, before, state(policy), normalizedReason)
    }

    @Transactional
    fun endOrganizationTrial(organizationId: UUID, reason: String): SubscriptionTrialTransition
    {
        val normalizedReason = validateReason(reason)
        val policy = subscriptionPolicyService.findOrganizationPolicyForUpdate(organizationId)
            ?: throw IllegalArgumentException("Organization subscription policy not found")
        val before = requireTrial(SubscriptionOwnerType.ORGANIZATION, state(policy))
        val now = Instant.now()
        val endedAt = before.currentPeriodEnd?.takeIf { it.isBefore(now) } ?: now
        val periodStart = before.currentPeriodStart
            ?: throw IllegalArgumentException("Trial current period start is missing")
        lifecycleValidator.validate(
            SubscriptionOwnerType.ORGANIZATION,
            SubscriptionLifecycleUpdate(
                planCode = PlanCode.BUSINESS,
                status = SubscriptionStatus.TRIALING,
                billingFrequency = null,
                currentPeriodStart = periodStart,
                currentPeriodEnd = endedAt,
                gracePeriodEnd = null,
                changeReason = normalizedReason,
            ),
        )
        policy.currentPeriodEnd = Timestamp.from(endedAt)
        policy.changeReason = normalizedReason
        policy.updatedDate = Timestamp.from(now)
        subscriptionPolicyService.updateOrganizationPolicy(policy)
        return transition(
            SubscriptionOwnerType.ORGANIZATION,
            organizationId,
            before,
            state(policy),
            normalizedReason,
        )
    }

    @Transactional
    fun convertUserTrial(
        appUserId: UUID,
        billingFrequency: BillingFrequency,
        paidPeriodEnd: Instant,
        reason: String,
    ): SubscriptionTrialTransition
    {
        val normalizedReason = validateReason(reason)
        val policy = subscriptionPolicyService.findUserPolicyForUpdate(appUserId)
            ?: throw IllegalArgumentException("User subscription policy not found")
        val before = requireTrial(SubscriptionOwnerType.USER, state(policy))
        val now = Instant.now()
        validatePaidLifecycle(
            SubscriptionOwnerType.USER,
            PlanCode.PERSONAL,
            billingFrequency,
            now,
            paidPeriodEnd,
            normalizedReason,
        )
        policy.subscriptionStatus = SubscriptionStatus.ACTIVE.name
        policy.billingFrequency = billingFrequency.name
        policy.currentPeriodStart = Timestamp.from(now)
        policy.currentPeriodEnd = Timestamp.from(paidPeriodEnd)
        policy.gracePeriodEnd = null
        policy.changeReason = normalizedReason
        policy.updatedDate = Timestamp.from(now)
        subscriptionPolicyService.updateUserPolicy(policy)
        return transition(SubscriptionOwnerType.USER, appUserId, before, state(policy), normalizedReason)
    }

    @Transactional
    fun convertOrganizationTrial(
        organizationId: UUID,
        billingFrequency: BillingFrequency,
        paidPeriodEnd: Instant,
        seatCapacity: Long,
        reason: String,
    ): SubscriptionTrialTransition
    {
        require(seatCapacity > 0) { "Purchased seat capacity must be greater than 0" }
        val normalizedReason = validateReason(reason)
        val policy = subscriptionPolicyService.findOrganizationPolicyForUpdate(organizationId)
            ?: throw IllegalArgumentException("Organization subscription policy not found")
        val before = requireTrial(SubscriptionOwnerType.ORGANIZATION, state(policy))
        val now = Instant.now()
        validatePaidLifecycle(
            SubscriptionOwnerType.ORGANIZATION,
            PlanCode.BUSINESS,
            billingFrequency,
            now,
            paidPeriodEnd,
            normalizedReason,
        )
        policy.maxUsers = seatCapacity
        policy.subscriptionStatus = SubscriptionStatus.ACTIVE.name
        policy.billingFrequency = billingFrequency.name
        policy.currentPeriodStart = Timestamp.from(now)
        policy.currentPeriodEnd = Timestamp.from(paidPeriodEnd)
        policy.gracePeriodEnd = null
        policy.changeReason = normalizedReason
        policy.updatedDate = Timestamp.from(now)
        subscriptionPolicyService.updateOrganizationPolicy(policy)
        return transition(
            SubscriptionOwnerType.ORGANIZATION,
            organizationId,
            before,
            state(policy),
            normalizedReason,
        )
    }

    private fun validatePaidLifecycle(
        ownerType: SubscriptionOwnerType,
        planCode: PlanCode,
        billingFrequency: BillingFrequency,
        periodStart: Instant,
        periodEnd: Instant,
        reason: String,
    )
    {
        require(periodEnd.isAfter(periodStart)) { "Paid period end must be in the future" }
        lifecycleValidator.validate(
            ownerType,
            SubscriptionLifecycleUpdate(
                planCode,
                SubscriptionStatus.ACTIVE,
                billingFrequency,
                periodStart,
                periodEnd,
                null,
                reason,
            ),
        )
    }

    private fun requireTrial(
        ownerType: SubscriptionOwnerType,
        trialState: SubscriptionTrialState,
    ): SubscriptionTrialState
    {
        val requiredPlan = if (ownerType == SubscriptionOwnerType.USER) PlanCode.PERSONAL else PlanCode.BUSINESS
        require(trialState.planCode == requiredPlan) { "Subscription does not hold the required paid trial plan" }
        require(trialState.status == SubscriptionStatus.TRIALING) { "Subscription is not trialing" }
        require(trialState.currentPeriodEnd != null) { "Trial current period end is missing" }
        return trialState
    }

    private fun validateReason(reason: String): String
    {
        val normalized = reason.trim()
        require(normalized.isNotEmpty()) { "Trial reason is required" }
        require(normalized.length <= 1024) { "Trial reason must be at most 1024 characters" }
        return normalized
    }

    private fun state(policy: com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy) = SubscriptionTrialState(
        PlanCode.fromCode(policy.planCode),
        SubscriptionStatus.fromCode(policy.subscriptionStatus),
        BillingFrequency.fromCodeOrNull(policy.billingFrequency),
        policy.currentPeriodStart?.toInstant(),
        policy.currentPeriodEnd?.toInstant(),
        null,
    )

    private fun state(policy: com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy) = SubscriptionTrialState(
        PlanCode.fromCode(policy.tierCode),
        SubscriptionStatus.fromCode(policy.subscriptionStatus),
        BillingFrequency.fromCodeOrNull(policy.billingFrequency),
        policy.currentPeriodStart?.toInstant(),
        policy.currentPeriodEnd?.toInstant(),
        policy.maxUsers,
    )

    private fun transition(
        ownerType: SubscriptionOwnerType,
        ownerId: UUID,
        before: SubscriptionTrialState,
        after: SubscriptionTrialState,
        reason: String,
    ) = SubscriptionTrialTransition(ownerType, ownerId, before, after, reason)
}
