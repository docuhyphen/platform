package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestCorrection
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionEvidence
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionItem
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionState
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewSnapshot
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestSatisfactionService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestReviewSettlement @Inject constructor(
    private val loader: InformationRequestReviewLoader,
    private val reviewRepository: InformationRequestReviewRepository,
    private val correctionRepository: InformationRequestCorrectionRepository,
    private val correctionItemRepository: InformationRequestCorrectionItemRepository,
    private val correctionEvidenceRepository: InformationRequestCorrectionEvidenceRepository,
    private val requestRepository: InformationRequestRepository,
    private val satisfaction: InformationRequestSatisfactionService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
    private val entityManager: EntityManager,
)
{
    fun settleIfDecided(
        locked: LockedInformationRequest,
        review: InformationRequestReview,
        actor: PrincipalRef,
        idempotencyKey: String,
    ): InformationRequestReview
    {
        if (review.state.settled) return review
        entityManager.flush()
        val snapshot = loader.snapshot(review)
        val settledState = snapshot.standing.settledState ?: return review
        val now = Timestamp.from(clock.instant())
        review.state = settledState
        review.settledAt = now
        review.settledByPrincipalKind = actor.kind
        review.settledByPrincipalId = actor.id
        review.reviewRevision += 1
        reviewRepository.update(review)
        val request = locked.request
        request.responseRevision += 1
        request.aggregateRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        entityManager.flush()
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.SETTLE_REVIEW,
                actor = actor,
                idempotencyKey = "information_request.review_settlement|${review.id}|$idempotencyKey",
                details = mapOf(
                    "reviewId" to review.id.toString(),
                    "submissionPackageId" to review.packageId.toString(),
                    "reviewOutcome" to settledState.name,
                ),
            ),
        )
        when (settledState)
        {
            InformationRequestReviewState.CHANGES_REQUESTED -> openCorrection(locked, snapshot, actor, idempotencyKey, now)
            InformationRequestReviewState.SATISFIED,
            InformationRequestReviewState.SATISFIED_WITH_EXCEPTION,
            -> satisfaction.closeIfSatisfied(locked, review.packageId, actor, idempotencyKey)
            else -> Unit
        }
        return review
    }

    private fun openCorrection(
        locked: LockedInformationRequest,
        snapshot: InformationRequestReviewSnapshot,
        actor: PrincipalRef,
        idempotencyKey: String,
        now: Timestamp,
    )
    {
        val returned = snapshot.reviewedItems.filter {
            snapshot.standing.itemOutcomes[it.id] == InformationRequestReviewOutcome.CHANGES_REQUIRED
        }
        val correction = correctionRepository.save(
            InformationRequestCorrection().apply {
                informationRequestId = snapshot.review.informationRequestId
                reviewId = snapshot.review.id
                packageId = snapshot.review.packageId
                state = InformationRequestCorrectionState.OPEN
                openedAt = now
            },
        )
        entityManager.flush()
        val correctionItems = returned.map { item ->
            correctionItemRepository.save(
                InformationRequestCorrectionItem().apply {
                    correctionId = correction.id
                    informationRequestId = correction.informationRequestId
                    submissionItemId = item.id
                    requirementId = item.informationRequestRequirementId
                },
            )
        }
        entityManager.flush()
        val byItem = correctionItems.associateBy { it.submissionItemId }
        snapshot.findings
            .filter { it.correctionScope == InformationRequestFindingCorrectionScope.EVIDENCE_VERSION && it.submissionItemId in byItem }
            .mapNotNull { finding ->
                val member = snapshot.submission.evidence.firstOrNull {
                    it.itemId == finding.submissionItemId && it.evidenceVersionId == finding.evidenceVersionId
                } ?: return@mapNotNull null
                byItem.getValue(finding.submissionItemId) to member
            }
            .distinctBy { it.second.evidenceVersionId }
            .forEach { (correctionItem, member) ->
                correctionEvidenceRepository.save(
                    InformationRequestCorrectionEvidence().apply {
                        correctionId = correction.id
                        this.correctionItemId = correctionItem.id
                        informationRequestId = correction.informationRequestId
                        evidenceArtifactId = member.evidenceArtifactId
                        evidenceVersionId = member.evidenceVersionId
                    },
                )
            }
        val request = locked.request
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.REQUEST_CORRECTION,
                actor = actor,
                idempotencyKey = "information_request.correction|${correction.id}|$idempotencyKey",
                details = mapOf(
                    "correctionId" to correction.id.toString(),
                    "reviewId" to snapshot.review.id.toString(),
                    "submissionPackageId" to snapshot.review.packageId.toString(),
                    "returnedRequirementCount" to correctionItems.size.toString(),
                ) + (snapshot.submission.submissionPackage.stageKey?.let { mapOf("stageKey" to it) } ?: emptyMap()),
            ),
        )
    }
}
