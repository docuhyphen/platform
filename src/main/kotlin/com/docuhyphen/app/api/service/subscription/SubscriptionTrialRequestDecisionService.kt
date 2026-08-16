package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.SubscriptionTrialRequestStatus
import com.docuhyphen.app.api.repository.subscription.SubscriptionTrialRequestRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class SubscriptionTrialRequestDecisionService @Inject constructor(
    private val repository: SubscriptionTrialRequestRepository,
    private val eligibilityService: SubscriptionTrialRequestEligibilityService,
    private val trialService: SubscriptionTrialService,
    private val requestService: SubscriptionTrialRequestService,
)
{
    @Transactional
    fun decide(
        requestId: UUID,
        decision: SubscriptionTrialRequestStatus,
        durationDays: Int?,
        seatCapacity: Long?,
        reason: String,
        reviewedByAppUserId: UUID,
    ): SubscriptionTrialRequestView
    {
        require(decision == SubscriptionTrialRequestStatus.APPROVED || decision == SubscriptionTrialRequestStatus.REJECTED) {
            "Trial request status must be APPROVED or REJECTED"
        }
        val normalizedReason = validateReason(reason)
        val request = repository.findByIdForDecision(requestId)
            ?: throw IllegalArgumentException("Trial request not found")
        require(request.status == SubscriptionTrialRequestStatus.PENDING.name) { "Trial request has already been reviewed" }
        if (decision == SubscriptionTrialRequestStatus.APPROVED)
        {
            val ownerType = SubscriptionOwnerType.valueOf(request.ownerType)
            val eligibility = eligibilityService.evaluateForRequest(ownerType, request.ownerId)
            require(eligibility.eligible) { eligibility.reason ?: "This subscription is no longer eligible for a trial" }
            val mutation = when (ownerType)
            {
                SubscriptionOwnerType.USER ->
                {
                    require(seatCapacity == null) { "Seat capacity is only valid for an organization trial" }
                    trialService.startUserTrial(
                        request.ownerId,
                        durationDays ?: SubscriptionTrialService.DEFAULT_USER_DURATION_DAYS,
                        normalizedReason,
                        reviewedByAppUserId,
                    )
                }
                SubscriptionOwnerType.ORGANIZATION -> trialService.startOrganizationTrial(
                    request.ownerId,
                    durationDays ?: SubscriptionTrialService.DEFAULT_ORGANIZATION_DURATION_DAYS,
                    seatCapacity ?: SubscriptionTrialService.DEFAULT_ORGANIZATION_SEAT_CAPACITY,
                    normalizedReason,
                    reviewedByAppUserId,
                )
            }
            request.trialGrantId = mutation.grant.id
        }
        else
        {
            require(durationDays == null && seatCapacity == null) {
                "Duration and seat capacity are only valid when approving a request"
            }
        }
        val now = Timestamp.from(Instant.now())
        request.status = decision.name
        request.reviewedByAppUserId = reviewedByAppUserId
        request.reviewedAt = now
        request.decisionReason = normalizedReason
        request.updatedAt = now
        repository.update(request)
        val view = requestService.view(request)
        requestService.notifyDecision(view)
        return view
    }

    private fun validateReason(reason: String): String
    {
        val normalized = reason.trim()
        require(normalized.isNotEmpty()) { "Decision reason is required" }
        require(normalized.length <= 1024) { "Decision reason must be at most 1024 characters" }
        return normalized
    }
}
