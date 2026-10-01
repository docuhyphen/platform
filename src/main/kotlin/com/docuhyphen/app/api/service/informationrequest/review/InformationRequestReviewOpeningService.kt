package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestCorrection
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionState
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecision
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewRemediation
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewItemStanding
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStageState
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewFindingRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRemediationRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestReviewOpeningService @Inject constructor(
    private val loader: InformationRequestReviewLoader,
    private val reviewRepository: InformationRequestReviewRepository,
    private val assignmentRepository: InformationRequestReviewAssignmentRepository,
    private val decisionRepository: InformationRequestReviewDecisionRepository,
    private val findingRepository: InformationRequestReviewFindingRepository,
    private val correctionRepository: InformationRequestCorrectionRepository,
    private val correctionItemRepository: InformationRequestCorrectionItemRepository,
    private val remediationRepository: InformationRequestReviewRemediationRepository,
    private val itemRepository: InformationRequestSubmissionItemRepository,
    private val settlement: InformationRequestReviewSettlement,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
    private val entityManager: EntityManager,
)
{
    fun onSubmitted(
        locked: LockedInformationRequest,
        submission: InformationRequestSubmissionPackage,
        corrected: InformationRequestCorrection?,
        actor: PrincipalRef,
        idempotencyKey: String,
    ): InformationRequestReview?
    {
        val now = Timestamp.from(clock.instant())
        corrected?.let { recordResubmission(it, submission, now) }
        if (!submission.reviewRequired) return null

        val prior = corrected?.let { reviewRepository.findById(it.reviewId) }
        val review = reviewRepository.save(
            InformationRequestReview().apply {
                informationRequestId = submission.informationRequestId
                packageId = submission.id
                reviewNumber = reviewRepository.nextReviewNumber(submission.informationRequestId)
                kind = if (prior == null) InformationRequestReviewKind.INITIAL else InformationRequestReviewKind.RESUBMISSION
                priorReviewId = prior?.id
                templateVersionId = submission.templateVersionId
                state = InformationRequestReviewState.PENDING
                reviewRevision = 1
                openedByPrincipalKind = actor.kind
                openedByPrincipalId = actor.id
                openedAt = now
            },
        )
        entityManager.flush()
        prior?.let { carryForward(it, review, now) }
        recordStart(locked, review, actor, idempotencyKey)
        return settlement.settleIfDecided(locked, review, actor, idempotencyKey)
    }

    fun requireWithdrawable(submission: InformationRequestSubmissionPackage, actor: PrincipalRef)
    {
        val reviews = reviewRepository.findForRequest(submission.informationRequestId).filter { it.packageId == submission.id }
        val underReview = reviews.any { it.state != InformationRequestReviewState.PENDING } ||
            assignmentRepository.findForReviews(reviews.map { it.id }).isNotEmpty()
        if (underReview)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUBMISSION_NOT_WITHDRAWABLE,
                "This Submission Package is under review and can no longer be withdrawn",
            )
        }
        val now = Timestamp.from(clock.instant())
        reviews.forEach { review ->
            review.state = InformationRequestReviewState.WITHDRAWN
            review.settledAt = now
            review.settledByPrincipalKind = actor.kind
            review.settledByPrincipalId = actor.id
            review.reviewRevision += 1
            reviewRepository.update(review)
        }
    }

    fun recordStart(
        locked: LockedInformationRequest,
        review: InformationRequestReview,
        actor: PrincipalRef,
        idempotencyKey: String,
    )
    {
        val request = locked.request
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.START_REVIEW,
                actor = actor,
                idempotencyKey = "information_request.review_start|${review.id}|$idempotencyKey",
                details = mapOf(
                    "reviewId" to review.id.toString(),
                    "reviewKind" to review.kind.name,
                    "submissionPackageId" to review.packageId.toString(),
                ) + (review.priorReviewId?.let { mapOf("priorReviewId" to it.toString()) } ?: emptyMap()),
            ),
        )
    }

    private fun recordResubmission(
        correction: InformationRequestCorrection,
        submission: InformationRequestSubmissionPackage,
        now: Timestamp,
    )
    {
        correction.state = InformationRequestCorrectionState.RESUBMITTED
        correction.closedAt = now
        correction.resubmittedPackageId = submission.id
        correctionRepository.update(correction)
        entityManager.flush()
        val returned = correctionItemRepository.findForCorrections(listOf(correction.id)).map { it.requirementId }.toSet()
        val resubmittedItems = itemRepository.findForPackages(listOf(submission.id))
            .associateBy { it.informationRequestRequirementId }
        findingRepository.findForReview(correction.reviewId)
            .filter { it.correctionScope != InformationRequestFindingCorrectionScope.NONE && it.requirementId in returned }
            .forEach { finding ->
                val remediatedBy = resubmittedItems[finding.requirementId] ?: return@forEach
                remediationRepository.save(
                    InformationRequestReviewRemediation().apply {
                        informationRequestId = correction.informationRequestId
                        correctionId = correction.id
                        findingId = finding.id
                        remediatedByPackageId = submission.id
                        remediatedByItemId = remediatedBy.id
                        recordedAt = now
                    },
                )
            }
    }

    private fun carryForward(prior: InformationRequestReview, review: InformationRequestReview, now: Timestamp)
    {
        val priorSnapshot = loader.snapshot(prior)
        val current = loader.snapshot(review)
        val priorItems = priorSnapshot.submission.items.associateBy { it.informationRequestRequirementId }
        var sequence = decisionRepository.nextSequenceNumber(review.id)
        current.plan.stages.forEach { stage ->
            val priorStage = priorSnapshot.standing.stage(stage.stageKey)
                ?.takeIf { it.state == InformationRequestReviewStageState.SETTLED }
                ?: return@forEach
            current.coverage[stage.stageKey].orEmpty().forEach { itemId ->
                val item = current.item(itemId) ?: return@forEach
                val priorItem = priorItems[item.informationRequestRequirementId]
                    ?.takeIf { it.itemHashSha256 == item.itemHashSha256 }
                    ?: return@forEach
                val priorResult = priorStage.items.firstOrNull { it.itemId == priorItem.id }
                    ?.takeIf { it.standing == InformationRequestReviewItemStanding.DECIDED && it.outcome?.passing == true }
                    ?: return@forEach
                val source = priorSnapshot.decisions
                    .filter { it.stageKey == stage.stageKey && it.submissionItemId == priorItem.id }
                    .maxByOrNull { it.sequenceNumber }
                    ?: return@forEach
                decisionRepository.save(
                    InformationRequestReviewDecision().apply {
                        reviewId = review.id
                        informationRequestId = review.informationRequestId
                        packageId = review.packageId
                        submissionItemId = item.id
                        requirementId = item.informationRequestRequirementId
                        templateReviewStageId = stage.id
                        stageKey = stage.stageKey
                        kind = InformationRequestReviewDecisionKind.CARRIED
                        carriedFromDecisionId = source.id
                        outcome = priorResult.outcome!!
                        decidedByPrincipalKind = source.decidedByPrincipalKind
                        decidedByPrincipalId = source.decidedByPrincipalId
                        decidedAt = now
                        sequenceNumber = sequence++
                    },
                )
                entityManager.flush()
            }
        }
    }
}
