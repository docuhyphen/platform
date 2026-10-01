package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignment
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecision
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewFindingRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateReviewStageRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateReviewStageSectionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.command.RevisionETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionPackageReader
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestReviewLoader @Inject constructor(
    private val reviewRepository: InformationRequestReviewRepository,
    private val assignmentRepository: InformationRequestReviewAssignmentRepository,
    private val decisionRepository: InformationRequestReviewDecisionRepository,
    private val findingRepository: InformationRequestReviewFindingRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val stageRepository: InformationRequestTemplateReviewStageRepository,
    private val stageSectionRepository: InformationRequestTemplateReviewStageSectionRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val packageReader: InformationRequestSubmissionPackageReader,
)
{
    fun plan(templateVersionId: UUID): InformationRequestReviewPlan
    {
        val version = versionRepository.findById(templateVersionId)
            ?: throw IllegalStateException("Information Request Template Version not found")
        val coveredByStage = stageSectionRepository.findForVersion(templateVersionId)
            .groupBy({ it.reviewStageId }, { it.templateSectionId })
        return InformationRequestReviewPlan(
            templateVersionId = templateVersionId,
            ordering = version.reviewStageOrdering,
            stages = stageRepository.findForVersion(templateVersionId).map { stage ->
                InformationRequestReviewStagePlan(
                    id = stage.id,
                    stageKey = stage.stageKey,
                    position = stage.position,
                    title = stage.title,
                    aggregation = stage.aggregation,
                    quorumCount = stage.quorumCount,
                    minimumReviewerCount = stage.minimumReviewerCount,
                    tieResolution = stage.tieResolution,
                    overridePermitted = stage.overridePermitted,
                    excludesResponseParties = stage.excludesResponseParties,
                    excludesPriorReviewers = stage.excludesPriorReviewers,
                    coveredSectionIds = coveredByStage[stage.id].orEmpty().toSet(),
                )
            },
        )
    }

    fun requireReview(requestId: UUID, reviewId: UUID): InformationRequestReview =
        reviewRepository.findById(reviewId)?.takeIf { it.informationRequestId == requestId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Review not found")

    fun snapshot(review: InformationRequestReview): InformationRequestReviewSnapshot
    {
        val view = packageReader.view(review.informationRequestId, review.packageId)
        val plan = plan(review.templateVersionId)
        val bindings = bindingRepository.findOrdered(review.templateVersionId).associateBy { it.id }
        val reviewed = view.items.filter { item ->
            val binding = bindings[item.templateBindingId] ?: return@filter false
            InformationRequestReviewRouting.routes(item, binding.reviewPolicy)
        }
        val coverage = plan.stages.associate { stage ->
            stage.stageKey to reviewed
                .filter { item -> bindings[item.templateBindingId]?.let { stage.coversSection(it.templateSectionId) } == true }
                .map { it.id }
        }
        val assignments = assignmentRepository.findForReview(review.id)
        val decisions = decisionRepository.findForReview(review.id)
        val standing = InformationRequestReviewAggregator.evaluate(
            plan.ordering,
            plan.stages.map { InformationRequestReviewStageInput(it, coverage[it.stageKey].orEmpty()) },
            reviewed.map { it.id },
            assignments.map(::assignmentFact),
            decisions.map(::decisionFact),
        )
        return InformationRequestReviewSnapshot(
            review = review,
            submission = view,
            plan = plan,
            reviewedItems = reviewed,
            coverage = coverage,
            assignments = assignments,
            decisions = decisions,
            findings = findingRepository.findForReview(review.id),
            standing = standing,
        )
    }

    fun latestForPackage(requestId: UUID, packageId: UUID): InformationRequestReview? =
        reviewRepository.findForRequest(requestId).filter { it.packageId == packageId }.maxByOrNull { it.reviewNumber }

    fun reviewETag(review: InformationRequestReview): String = RevisionETag.of(review.id, review.reviewRevision)

    fun draftETag(assignment: InformationRequestReviewAssignment): String =
        RevisionETag.of(assignment.id, assignment.draftRevision)

    private fun assignmentFact(assignment: InformationRequestReviewAssignment) =
        InformationRequestReviewAssignmentFact(assignment.id, assignment.stageKey, assignment.state)

    private fun decisionFact(decision: InformationRequestReviewDecision) =
        InformationRequestReviewDecisionFact(
            itemId = decision.submissionItemId,
            stageKey = decision.stageKey,
            assignmentId = decision.assignmentId,
            kind = decision.kind,
            outcome = decision.outcome,
            sequenceNumber = decision.sequenceNumber,
        )
}
