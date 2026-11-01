package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewItemStanding
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStageState
import com.docuhyphen.app.api.repository.informationrequest.review.*
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
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
        actor: InformationRequestAuditActor,
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
                kind =
                    if (prior == null) InformationRequestReviewKind.INITIAL else InformationRequestReviewKind.RESUBMISSION
                priorReviewId = prior?.id
                templateVersionId = submission.templateVersionId
                state = InformationRequestReviewState.PENDING
                reviewRevision = 1
                openedByPrincipalKind = actor.principal.kind
                openedByPrincipalId = actor.principal.id
                openedAt = now
            },
        )
        entityManager.flush()
        prior?.let { carryForward(it, review, now) }
        recordStart(locked, review, actor, idempotencyKey)
        return settlement.settleIfDecided(locked, review, actor, idempotencyKey)
    }

    fun withdrawalRefusal(submission: InformationRequestSubmissionPackage): String?
    {
        if (correctionRepository.findForRequest(submission.informationRequestId)
                .any { it.resubmittedPackageId == submission.id }
        ) {
            return "This Submission Package answers a correction and can no longer be withdrawn"
        }
        val reviews = reviewsOf(submission)
        val underReview = reviews.any { it.state != InformationRequestReviewState.PENDING } ||
                assignmentRepository.findForReviews(reviews.map { it.id }).isNotEmpty()
        return "This Submission Package is under review and can no longer be withdrawn".takeIf { underReview }
    }

    fun requireWithdrawable(submission: InformationRequestSubmissionPackage, actor: PrincipalRef) {
        withdrawalRefusal(submission)?.let { refusal ->
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUBMISSION_NOT_WITHDRAWABLE,
                refusal
            )
        }
        val reviews = reviewsOf(submission)
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

    private fun reviewsOf(submission: InformationRequestSubmissionPackage): List<InformationRequestReview> =
        reviewRepository.findForRequest(submission.informationRequestId).filter { it.packageId == submission.id }

    fun recordStart(
        locked: LockedInformationRequest,
        review: InformationRequestReview,
        actor: InformationRequestAuditActor,
        idempotencyKey: String,
        additionalDetails: Map<String, String> = emptyMap(),
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
                auditIdempotencyKey = "information_request.review_start|${review.id}|$idempotencyKey",
                details = mapOf(
                    "reviewId" to review.id.toString(),
                    "reviewKind" to review.kind.name,
                    "submissionPackageId" to review.packageId.toString(),
                ) + (review.priorReviewId?.let { mapOf("priorReviewId" to it.toString()) }
                    ?: emptyMap()) + additionalDetails,
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
        val returned =
            correctionItemRepository.findForCorrections(listOf(correction.id)).map { it.requirementId }.toSet()
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
