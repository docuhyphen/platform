package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ApplicationScoped
class SubscriptionTrialPolicyMutationService @Inject constructor(
    private val subscriptionPolicyService: SubscriptionPolicyService,
    private val lifecycleValidator: SubscriptionLifecycleValidator,
)
{
    fun startUserTrial(appUserId: UUID, durationDays: Int, reason: String): SubscriptionTrialPolicyMutation
    {
        subscriptionPolicyService.ensureUserPolicy(appUserId)
        val policy = subscriptionPolicyService.findUserPolicyForUpdate(appUserId)
            ?: throw IllegalStateException("User subscription policy could not be locked")
        val now = Instant.now()
        val periodEnd = trialEnd(now, durationDays)
        requireEligible(
            planCode = PlanCode.fromCode(policy.planCode),
            status = SubscriptionStatus.fromCode(policy.subscriptionStatus),
            billingFrequency = BillingFrequency.fromCodeOrNull(policy.billingFrequency),
            currentPeriodEnd = policy.currentPeriodEnd?.toInstant(),
            externalBillingCustomerRef = policy.externalBillingCustomerRef,
            externalBillingSubscriptionRef = policy.externalBillingSubscriptionRef,
            now = now,
        )
        val before = state(policy)
        validate(SubscriptionOwnerType.USER, PlanCode.PERSONAL, now, periodEnd, reason)

        policy.planCode = PlanCode.PERSONAL.name
        applyTrialLifecycle(policy, now, periodEnd, reason)
        subscriptionPolicyService.updateUserPolicy(policy)
        return mutation(SubscriptionOwnerType.USER, appUserId, before, state(policy), now, periodEnd)
    }

    fun startOrganizationTrial(
        organizationId: UUID,
        durationDays: Int,
        seatCapacity: Long,
        reason: String,
    ): SubscriptionTrialPolicyMutation
    {
        require(seatCapacity > 0) { "Trial seat capacity must be greater than 0" }
        subscriptionPolicyService.ensureOrganizationPolicy(organizationId)
        val policy = subscriptionPolicyService.findOrganizationPolicyForUpdate(organizationId)
            ?: throw IllegalStateException("Organization subscription policy could not be locked")
        val now = Instant.now()
        val periodEnd = trialEnd(now, durationDays)
        requireEligible(
            planCode = PlanCode.fromCode(policy.tierCode),
            status = SubscriptionStatus.fromCode(policy.subscriptionStatus),
            billingFrequency = BillingFrequency.fromCodeOrNull(policy.billingFrequency),
            currentPeriodEnd = policy.currentPeriodEnd?.toInstant(),
            externalBillingCustomerRef = policy.externalBillingCustomerRef,
            externalBillingSubscriptionRef = policy.externalBillingSubscriptionRef,
            now = now,
        )
        val before = state(policy)
        validate(SubscriptionOwnerType.ORGANIZATION, PlanCode.BUSINESS, now, periodEnd, reason)

        policy.tierCode = PlanCode.BUSINESS.name
        policy.maxUsers = seatCapacity
        applyTrialLifecycle(policy, now, periodEnd, reason)
        subscriptionPolicyService.updateOrganizationPolicy(policy)
        return mutation(SubscriptionOwnerType.ORGANIZATION, organizationId, before, state(policy), now, periodEnd)
    }

    fun extendUserTrial(
        appUserId: UUID,
        newPeriodEnd: Instant,
        reason: String,
    ): SubscriptionTrialPolicyMutation
    {
        val policy = subscriptionPolicyService.findUserPolicyForUpdate(appUserId)
            ?: throw IllegalArgumentException("User subscription policy not found")
        val before = validateExtension(SubscriptionOwnerType.USER, state(policy), newPeriodEnd, reason)
        val previousEnd = before.currentPeriodEnd!!
        policy.currentPeriodEnd = Timestamp.from(newPeriodEnd)
        policy.changeReason = reason
        policy.updatedDate = Timestamp.from(Instant.now())
        subscriptionPolicyService.updateUserPolicy(policy)
        return mutation(SubscriptionOwnerType.USER, appUserId, before, state(policy), previousEnd, newPeriodEnd)
    }

    fun extendOrganizationTrial(
        organizationId: UUID,
        newPeriodEnd: Instant,
        reason: String,
    ): SubscriptionTrialPolicyMutation
    {
        val policy = subscriptionPolicyService.findOrganizationPolicyForUpdate(organizationId)
            ?: throw IllegalArgumentException("Organization subscription policy not found")
        val before = validateExtension(SubscriptionOwnerType.ORGANIZATION, state(policy), newPeriodEnd, reason)
        val previousEnd = before.currentPeriodEnd!!
        policy.currentPeriodEnd = Timestamp.from(newPeriodEnd)
        policy.changeReason = reason
        policy.updatedDate = Timestamp.from(Instant.now())
        subscriptionPolicyService.updateOrganizationPolicy(policy)
        return mutation(
            SubscriptionOwnerType.ORGANIZATION,
            organizationId,
            before,
            state(policy),
            previousEnd,
            newPeriodEnd,
        )
    }

    private fun validateExtension(
        ownerType: SubscriptionOwnerType,
        state: SubscriptionTrialState,
        newPeriodEnd: Instant,
        reason: String,
    ): SubscriptionTrialState
    {
        val requiredPlan = if (ownerType == SubscriptionOwnerType.USER) PlanCode.PERSONAL else PlanCode.BUSINESS
        require(state.planCode == requiredPlan) { "Subscription does not hold the required paid trial plan" }
        require(state.status == SubscriptionStatus.TRIALING) { "Subscription is not trialing" }
        val periodStart = state.currentPeriodStart
            ?: throw IllegalArgumentException("Trial current period start is missing")
        val previousEnd = state.currentPeriodEnd
            ?: throw IllegalArgumentException("Trial current period end is missing")
        require(newPeriodEnd.isAfter(previousEnd)) { "Extended trial end must be after the current trial end" }
        require(newPeriodEnd.isAfter(Instant.now())) { "Extended trial end must be in the future" }
        validate(ownerType, state.planCode, periodStart, newPeriodEnd, reason)
        return state
    }

    private fun validate(
        ownerType: SubscriptionOwnerType,
        planCode: PlanCode,
        periodStart: Instant,
        periodEnd: Instant,
        reason: String,
    )
    {
        lifecycleValidator.validate(
            ownerType,
            SubscriptionLifecycleUpdate(
                planCode = planCode,
                status = SubscriptionStatus.TRIALING,
                billingFrequency = null,
                currentPeriodStart = periodStart,
                currentPeriodEnd = periodEnd,
                gracePeriodEnd = null,
                changeReason = reason,
            ),
        )
    }

    private fun applyTrialLifecycle(
        policy: UserSubscriptionPolicy,
        periodStart: Instant,
        periodEnd: Instant,
        reason: String,
    )
    {
        policy.subscriptionStatus = SubscriptionStatus.TRIALING.name
        policy.billingFrequency = null
        policy.currentPeriodStart = Timestamp.from(periodStart)
        policy.currentPeriodEnd = Timestamp.from(periodEnd)
        policy.gracePeriodEnd = null
        policy.changeReason = reason
        policy.updatedDate = Timestamp.from(periodStart)
    }

    private fun applyTrialLifecycle(
        policy: OrganizationSubscriptionPolicy,
        periodStart: Instant,
        periodEnd: Instant,
        reason: String,
    )
    {
        policy.subscriptionStatus = SubscriptionStatus.TRIALING.name
        policy.billingFrequency = null
        policy.currentPeriodStart = Timestamp.from(periodStart)
        policy.currentPeriodEnd = Timestamp.from(periodEnd)
        policy.gracePeriodEnd = null
        policy.changeReason = reason
        policy.updatedDate = Timestamp.from(periodStart)
    }

    private fun state(policy: UserSubscriptionPolicy): SubscriptionTrialState = SubscriptionTrialState(
        planCode = PlanCode.fromCode(policy.planCode),
        status = SubscriptionStatus.fromCode(policy.subscriptionStatus),
        billingFrequency = BillingFrequency.fromCodeOrNull(policy.billingFrequency),
        currentPeriodStart = policy.currentPeriodStart?.toInstant(),
        currentPeriodEnd = policy.currentPeriodEnd?.toInstant(),
        seatCapacity = null,
    )

    private fun state(policy: OrganizationSubscriptionPolicy): SubscriptionTrialState = SubscriptionTrialState(
        planCode = PlanCode.fromCode(policy.tierCode),
        status = SubscriptionStatus.fromCode(policy.subscriptionStatus),
        billingFrequency = BillingFrequency.fromCodeOrNull(policy.billingFrequency),
        currentPeriodStart = policy.currentPeriodStart?.toInstant(),
        currentPeriodEnd = policy.currentPeriodEnd?.toInstant(),
        seatCapacity = policy.maxUsers,
    )

    private fun mutation(
        ownerType: SubscriptionOwnerType,
        ownerId: UUID,
        before: SubscriptionTrialState,
        after: SubscriptionTrialState,
        intervalStart: Instant,
        intervalEnd: Instant,
    ): SubscriptionTrialPolicyMutation = SubscriptionTrialPolicyMutation(
        ownerType,
        ownerId,
        before,
        after,
        intervalStart,
        intervalEnd,
    )

    private fun trialEnd(start: Instant, durationDays: Int): Instant
    {
        return runCatching { start.plus(durationDays.toLong(), ChronoUnit.DAYS) }
            .getOrElse { throw IllegalArgumentException("Trial duration is too large") }
    }

    private fun requireEligible(
        planCode: PlanCode,
        status: SubscriptionStatus,
        billingFrequency: BillingFrequency?,
        currentPeriodEnd: Instant?,
        externalBillingCustomerRef: String?,
        externalBillingSubscriptionRef: String?,
        now: Instant,
    )
    {
        val eligibility = SubscriptionTrialEligibilityPolicy.evaluate(
            planCode,
            status,
            billingFrequency,
            currentPeriodEnd,
            externalBillingCustomerRef,
            externalBillingSubscriptionRef,
            now,
        )
        require(eligibility.eligible) { eligibility.reason ?: "This subscription is not eligible for a trial" }
    }
}
