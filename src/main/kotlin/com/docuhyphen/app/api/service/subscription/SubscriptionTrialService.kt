package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.SubscriptionTrialGrant
import com.docuhyphen.app.api.repository.SubscriptionTrialGrantRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class SubscriptionTrialService @Inject constructor(
    private val policyMutationService: SubscriptionTrialPolicyMutationService,
    private val trialGrantRepository: SubscriptionTrialGrantRepository,
)
{
    companion object
    {
        const val DEFAULT_USER_DURATION_DAYS: Int = 14
        const val DEFAULT_ORGANIZATION_DURATION_DAYS: Int = 30
        const val DEFAULT_ORGANIZATION_SEAT_CAPACITY: Long = 5
    }

    @Transactional
    fun startUserTrial(
        appUserId: UUID,
        durationDays: Int,
        reason: String,
        grantedByAppUserId: UUID,
    ): SubscriptionTrialMutation
    {
        val normalizedReason = validate(reason, durationDays)
        return record(
            policyMutationService.startUserTrial(appUserId, durationDays, normalizedReason),
            normalizedReason,
            grantedByAppUserId,
        )
    }

    @Transactional
    fun startOrganizationTrial(
        organizationId: UUID,
        durationDays: Int,
        seatCapacity: Long,
        reason: String,
        grantedByAppUserId: UUID,
    ): SubscriptionTrialMutation
    {
        require(seatCapacity > 0) { "Trial seat capacity must be greater than 0" }
        val normalizedReason = validate(reason, durationDays)
        return record(
            policyMutationService.startOrganizationTrial(
                organizationId,
                durationDays,
                seatCapacity,
                normalizedReason,
            ),
            normalizedReason,
            grantedByAppUserId,
        )
    }

    @Transactional
    fun extendUserTrial(
        appUserId: UUID,
        newPeriodEnd: Instant,
        reason: String,
        grantedByAppUserId: UUID,
    ): SubscriptionTrialMutation
    {
        val normalizedReason = validateReason(reason)
        return record(
            policyMutationService.extendUserTrial(appUserId, newPeriodEnd, normalizedReason),
            normalizedReason,
            grantedByAppUserId,
        )
    }

    @Transactional
    fun extendOrganizationTrial(
        organizationId: UUID,
        newPeriodEnd: Instant,
        reason: String,
        grantedByAppUserId: UUID,
    ): SubscriptionTrialMutation
    {
        val normalizedReason = validateReason(reason)
        return record(
            policyMutationService.extendOrganizationTrial(organizationId, newPeriodEnd, normalizedReason),
            normalizedReason,
            grantedByAppUserId,
        )
    }

    fun hasConsumedAutomaticTrial(ownerType: SubscriptionOwnerType, ownerId: UUID): Boolean
    {
        return trialGrantRepository.hasAutomaticGrant(ownerType, ownerId)
    }

    private fun record(
        mutation: SubscriptionTrialPolicyMutation,
        reason: String,
        grantedByAppUserId: UUID,
    ): SubscriptionTrialMutation
    {
        val grant = trialGrantRepository.save(
            SubscriptionTrialGrant().apply {
                ownerType = mutation.ownerType.name
                ownerId = mutation.ownerId
                planCode = mutation.after.planCode.name
                startedAt = Timestamp.from(mutation.grantedIntervalStart)
                endedAt = Timestamp.from(mutation.grantedIntervalEnd)
                source = SubscriptionTrialSource.PLATFORM_ADMIN.name
                this.grantedByAppUserId = grantedByAppUserId
                this.reason = reason
                createdAt = Timestamp.from(Instant.now())
            },
        )
        return SubscriptionTrialMutation(
            mutation.ownerType,
            mutation.ownerId,
            mutation.before,
            mutation.after,
            grant,
        )
    }

    private fun validate(reason: String, durationDays: Int): String
    {
        require(durationDays > 0) { "Trial duration must be greater than 0 days" }
        return validateReason(reason)
    }

    private fun validateReason(reason: String): String
    {
        val normalized = reason.trim()
        require(normalized.isNotEmpty()) { "Trial reason is required" }
        require(normalized.length <= 1024) { "Trial reason must be at most 1024 characters" }
        return normalized
    }
}
