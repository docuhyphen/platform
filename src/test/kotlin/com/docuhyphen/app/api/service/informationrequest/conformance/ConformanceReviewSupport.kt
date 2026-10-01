package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestFindingSeverity
import com.docuhyphen.app.api.model.entity.InformationRequestRetestResult
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewFinding
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewVisibility
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.review.AssignInformationRequestReviewerCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewCommandResult
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewDraftPatch
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewDecisionsCommand
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewFindingCommand
import com.docuhyphen.app.api.model.informationrequest.review.SaveInformationRequestReviewDraftCommand
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import io.quarkus.narayana.jta.QuarkusTransaction
import java.util.UUID

internal class ConformanceReviewSupport(
    private val runtime: InformationRequestRuntimeTestServices,
    private val reviewRepository: InformationRequestReviewRepository,
)
{
    fun reviews(requestId: UUID): List<InformationRequestReview> =
        QuarkusTransaction.requiringNew().call { reviewRepository.findForRequest(requestId) }

    fun reviewETag(reviewId: UUID): String =
        QuarkusTransaction.requiringNew().call { runtime.reviewLoader.reviewETag(requireNotNull(reviewRepository.findById(reviewId))) }

    fun coverage(reviewId: UUID, stageKey: String): List<UUID> =
        QuarkusTransaction.requiringNew().call {
            runtime.reviewLoader.snapshot(requireNotNull(reviewRepository.findById(reviewId))).coverage[stageKey].orEmpty()
        }

    fun undecided(reviewId: UUID, stageKey: String): List<UUID> =
        QuarkusTransaction.requiringNew().call {
            val snapshot = runtime.reviewLoader.snapshot(requireNotNull(reviewRepository.findById(reviewId)))
            snapshot.coverage[stageKey].orEmpty().filterNot { id ->
                snapshot.decisions.any { it.submissionItemId == id && it.stageKey == stageKey && it.kind != InformationRequestReviewDecisionKind.REVIEWER }
            }
        }

    fun item(requestId: UUID, packageId: UUID, requirementId: UUID): UUID =
        QuarkusTransaction.requiringNew().call {
            runtime.packageReader.view(requestId, packageId).items.single { it.informationRequestRequirementId == requirementId }.id
        }

    @Suppress("LongParameterList")
    fun assign(
        services: InformationRequestRuntimeServices,
        requestId: UUID,
        reviewId: UUID,
        stageKey: String,
        reviewerPartyId: UUID,
        manager: RequestAccessContext,
        key: String,
    ): InformationRequestReviewCommandResult =
        QuarkusTransaction.requiringNew().call {
            services.reviewAssignments.assign(
                AssignInformationRequestReviewerCommand(
                    requestId = requestId,
                    reviewId = reviewId,
                    stageKey = stageKey,
                    reviewerPartyId = reviewerPartyId,
                    access = manager,
                    precondition = CommandPrecondition.ExpectedRevision(reviewETag(reviewId)),
                    idempotencyKey = key,
                ),
            )
        }

    @Suppress("LongParameterList")
    fun decide(
        services: InformationRequestRuntimeServices,
        requestId: UUID,
        assigned: InformationRequestReviewCommandResult,
        outcomes: Map<UUID, InformationRequestReviewOutcome>,
        reviewer: RequestAccessContext,
        key: String,
    ): InformationRequestReviewCommandResult
    {
        val draftETag = QuarkusTransaction.requiringNew().call {
            services.reviewDecisions.saveDraft(
                SaveInformationRequestReviewDraftCommand(
                    requestId = requestId,
                    reviewId = assigned.review.id,
                    assignmentId = requireNotNull(assigned.assignment).id,
                    patches = outcomes.map { (itemId, outcome) -> InformationRequestReviewDraftPatch(itemId, outcome, narrative = "Checked against the request") },
                    access = reviewer,
                    precondition = CommandPrecondition.ExpectedRevision(requireNotNull(assigned.draftETag)),
                ),
            )
        }.draftETag
        return QuarkusTransaction.requiringNew().call {
            services.reviewDecisions.recordDecisions(
                RecordInformationRequestReviewDecisionsCommand(
                    requestId = requestId,
                    reviewId = assigned.review.id,
                    assignmentId = requireNotNull(assigned.assignment).id,
                    access = reviewer,
                    precondition = CommandPrecondition.ExpectedRevision(draftETag),
                    idempotencyKey = key,
                ),
            )
        }
    }

    @Suppress("LongParameterList")
    fun finding(
        services: InformationRequestRuntimeServices,
        requestId: UUID,
        reviewId: UUID,
        itemId: UUID,
        severity: InformationRequestFindingSeverity,
        scope: InformationRequestFindingCorrectionScope,
        reviewer: RequestAccessContext,
        key: String,
        retests: UUID? = null,
        retestResult: InformationRequestRetestResult? = null,
    ): InformationRequestReviewFinding =
        QuarkusTransaction.requiringNew().call {
            services.reviewFindings.record(
                RecordInformationRequestReviewFindingCommand(
                    requestId = requestId,
                    reviewId = reviewId,
                    submissionItemId = itemId,
                    reasonCode = if (retests == null) "entry.incomplete" else "entry.rechecked",
                    narrative = if (retests == null) "The answer does not state what was asked" else "The answer now states what was asked",
                    severity = severity,
                    visibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
                    correctionScope = scope,
                    retestsFindingId = retests,
                    retestResult = retestResult,
                    access = reviewer,
                    idempotencyKey = key,
                ),
            )
        }.finding!!
}
