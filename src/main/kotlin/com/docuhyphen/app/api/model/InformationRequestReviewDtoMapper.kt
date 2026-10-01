package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestCorrectionDto
import com.docuhyphen.app.api.model.dto.InformationRequestRemediationDto
import com.docuhyphen.app.api.model.dto.InformationRequestRespondentReviewDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewAssignmentDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewCommandResultDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewCommentDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewDecisionDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewFindingDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewItemDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewItemStandingDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewQueueEntryDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewStageStandingDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewSummaryDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewWorksheetDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewWorksheetEntryDto
import com.docuhyphen.app.api.model.dto.InformationRequestSubmissionEvidenceDto
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewComment
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDraftItem
import com.docuhyphen.app.api.model.entity.InformationRequestReviewFinding
import com.docuhyphen.app.api.model.entity.InformationRequestReviewRemediation
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReadableReview
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestRespondentReviewResult
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewCommandResult
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewCorrectionView
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewDraftResult
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewQueueEntry
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.RevisionETag
import java.util.UUID

object InformationRequestReviewDtoMapper
{
    fun toDto(readable: InformationRequestReadableReview, caller: PrincipalRef): InformationRequestReviewDto
    {
        val snapshot = readable.snapshot
        val view = snapshot.submission
        val reviewedIds = snapshot.reviewedItems.map { it.id }.toSet()
        val evidenceByItem = view.evidence.groupBy { it.itemId }
        return InformationRequestReviewDto(
            review = summary(snapshot.review, view.submissionPackage.packageNumber, view.submissionPackage.stageKey),
            reviewStageOrdering = snapshot.plan.ordering,
            stages = snapshot.plan.stages.map { stage ->
                val standing = snapshot.standing.stage(stage.stageKey)
                InformationRequestReviewStageStandingDto(
                    stageKey = stage.stageKey,
                    title = stage.title,
                    position = stage.position,
                    aggregation = stage.aggregation,
                    quorumCount = stage.quorumCount,
                    minimumReviewerCount = stage.minimumReviewerCount,
                    tieResolution = stage.tieResolution,
                    overridePermitted = stage.overridePermitted,
                    excludesResponseParties = stage.excludesResponseParties,
                    excludesPriorReviewers = stage.excludesPriorReviewers,
                    state = requireNotNull(standing).state,
                    items = standing.items.map { InformationRequestReviewItemStandingDto(it.itemId, it.standing, it.outcome) },
                )
            },
            items = view.items.map { item ->
                val visible = item.id in readable.contentVisibleItemIds
                val fieldValue = item.fieldValueRevisionId?.let(readable.fieldValues::get)
                InformationRequestReviewItemDto(
                    submissionItemId = item.id,
                    requirementId = item.informationRequestRequirementId,
                    requirementKey = item.requirementKey,
                    requirementType = item.requirementType,
                    occurrencePath = item.occurrencePath,
                    reviewed = item.id in reviewedIds,
                    finalOutcome = snapshot.standing.itemOutcomes[item.id],
                    contentVisible = visible,
                    disposition = item.disposition.takeIf { visible },
                    narrative = item.narrative.takeIf { visible },
                    fieldValue = fieldValue?.value.takeIf { visible },
                    evidence = if (visible) evidenceByItem[item.id].orEmpty().map { member ->
                        InformationRequestSubmissionEvidenceDto(
                            artifactId = member.evidenceArtifactId,
                            evidenceVersionId = member.evidenceVersionId,
                            versionNumber = member.evidenceVersionNumber,
                            documentVersionId = member.documentVersionId,
                            contentHashAlgorithm = member.contentHashAlgorithm,
                            contentHash = member.contentHash,
                            contentLength = member.contentLength,
                            conformance = member.conformance,
                        )
                    } else emptyList(),
                )
            },
            assignments = snapshot.assignments.map { assignment ->
                InformationRequestReviewAssignmentDto(
                    id = assignment.id,
                    stageKey = assignment.stageKey,
                    reviewerPartyId = assignment.reviewerPartyId,
                    state = assignment.state,
                    dueAt = assignment.dueAt,
                    delegatedFromAssignmentId = assignment.delegatedFromAssignmentId,
                    assignedAt = assignment.assignedAt,
                    changeReasonCode = assignment.changeReasonCode,
                    changedAt = assignment.changedAt,
                    decidedAt = assignment.decidedAt,
                    callerIsReviewer = assignment.id in readable.callerAssignmentIds,
                )
            },
            decisions = snapshot.decisions.map { decision ->
                InformationRequestReviewDecisionDto(
                    id = decision.id,
                    stageKey = decision.stageKey,
                    submissionItemId = decision.submissionItemId,
                    kind = decision.kind,
                    assignmentId = decision.assignmentId,
                    carriedFromDecisionId = decision.carriedFromDecisionId,
                    outcome = decision.outcome,
                    narrative = decision.narrative,
                    decidedAt = decision.decidedAt,
                    decidedByCaller = decision.decidedByPrincipalKind == caller.kind && decision.decidedByPrincipalId == caller.id,
                )
            },
            findings = snapshot.findings.map { toDto(it, caller) },
            comments = readable.comments.map { toDto(it, caller) },
            correction = readable.correction?.let { toDto(it, null) },
            remediations = readable.remediations.map(::toDto),
            worksheets = readable.drafts.map { (assignmentId, entries) ->
                val assignment = snapshot.assignments.first { it.id == assignmentId }
                worksheet(assignmentId, RevisionETag.of(assignment.id, assignment.draftRevision), entries)
            },
            canManage = readable.canManage,
        )
    }

    fun toDto(result: InformationRequestRespondentReviewResult, caller: PrincipalRef): InformationRequestRespondentReviewDto =
        InformationRequestRespondentReviewDto(
            review = summary(result.review, result.packageNumber, result.stageKey),
            findings = result.visibleFindings.map { toDto(it, caller) },
            comments = result.visibleComments.map { toDto(it, caller) },
            correction = result.correction?.let { toDto(it, result.visibleRequirementIds) },
            remediations = result.remediations.map(::toDto),
            canAppeal = result.canAppeal,
            canComment = result.canComment,
        )

    fun toDto(entry: InformationRequestReviewQueueEntry): InformationRequestReviewQueueEntryDto =
        InformationRequestReviewQueueEntryDto(
            assignmentId = entry.assignment.id,
            reviewId = entry.review.id,
            informationRequestId = entry.request.id,
            exchangeId = entry.request.exchangeId,
            reviewStageKey = entry.assignment.stageKey,
            packageNumber = entry.packageNumber,
            submissionStageKey = entry.stageKey,
            reviewState = entry.review.state,
            itemCount = entry.itemCount,
            dueAt = entry.assignment.dueAt,
            assignedAt = entry.assignment.assignedAt,
        )

    fun toDto(result: InformationRequestReviewCommandResult, packageNumber: Int, stageKey: String?): InformationRequestReviewCommandResultDto =
        InformationRequestReviewCommandResultDto(
            requestState = result.request.state,
            responseETag = result.responseETag,
            review = summary(result.review, packageNumber, stageKey),
            assignmentId = result.assignment?.id,
            draftETag = result.draftETag,
            findingId = result.finding?.id,
            commentId = result.comment?.id,
        )

    fun toDto(result: InformationRequestReviewDraftResult): InformationRequestReviewWorksheetDto =
        worksheet(result.assignment.id, result.draftETag, result.items)

    private fun worksheet(assignmentId: UUID, draftETag: String, entries: List<InformationRequestReviewDraftItem>) =
        InformationRequestReviewWorksheetDto(
            assignmentId = assignmentId,
            draftETag = draftETag,
            entries = entries.map { InformationRequestReviewWorksheetEntryDto(it.submissionItemId, it.outcome, it.narrative) },
        )

    private fun summary(review: InformationRequestReview, packageNumber: Int, stageKey: String?) =
        InformationRequestReviewSummaryDto(
            id = review.id,
            informationRequestId = review.informationRequestId,
            packageId = review.packageId,
            packageNumber = packageNumber,
            stageKey = stageKey,
            reviewNumber = review.reviewNumber,
            kind = review.kind,
            priorReviewId = review.priorReviewId,
            state = review.state,
            openingReason = review.openingReason,
            openedAt = review.openedAt,
            settledAt = review.settledAt,
            reviewETag = RevisionETag.of(review.id, review.reviewRevision),
        )

    private fun toDto(finding: InformationRequestReviewFinding, caller: PrincipalRef) = InformationRequestReviewFindingDto(
        id = finding.id,
        submissionItemId = finding.submissionItemId,
        requirementId = finding.requirementId,
        evidenceVersionId = finding.evidenceVersionId,
        reasonCode = finding.reasonCode,
        narrative = finding.narrative,
        severity = finding.severity,
        visibility = finding.visibility,
        correctionScope = finding.correctionScope,
        retestsFindingId = finding.retestsFindingId,
        retestResult = finding.retestResult,
        recordedAt = finding.recordedAt,
        recordedByCaller = finding.recordedByPrincipalKind == caller.kind && finding.recordedByPrincipalId == caller.id,
    )

    private fun toDto(comment: InformationRequestReviewComment, caller: PrincipalRef) = InformationRequestReviewCommentDto(
        id = comment.id,
        submissionItemId = comment.submissionItemId,
        requirementId = comment.requirementId,
        findingId = comment.findingId,
        replyToCommentId = comment.replyToCommentId,
        authorRole = comment.authorRole,
        visibility = comment.visibility,
        body = comment.body,
        createdAt = comment.createdAt,
        authoredByCaller = comment.authorPrincipalKind == caller.kind && comment.authorPrincipalId == caller.id,
    )

    private fun toDto(view: InformationRequestReviewCorrectionView, visibleRequirementIds: Set<UUID>?): InformationRequestCorrectionDto
    {
        val visibleItems = view.items.filter { visibleRequirementIds == null || it.requirementId in visibleRequirementIds }
        val visibleItemIds = visibleItems.map { it.id }.toSet()
        return InformationRequestCorrectionDto(
            id = view.correction.id,
            reviewId = view.correction.reviewId,
            packageId = view.correction.packageId,
            state = view.correction.state,
            openedAt = view.correction.openedAt,
            closedAt = view.correction.closedAt,
            resubmittedPackageId = view.correction.resubmittedPackageId,
            requirementIds = visibleItems.map { it.requirementId },
            evidenceVersionIds = view.evidence.filter { it.correctionItemId in visibleItemIds }.map { it.evidenceVersionId },
            undisclosedItemCount = view.items.size - visibleItems.size,
        )
    }

    private fun toDto(remediation: InformationRequestReviewRemediation) = InformationRequestRemediationDto(
        findingId = remediation.findingId,
        remediatedByPackageId = remediation.remediatedByPackageId,
        remediatedByItemId = remediation.remediatedByItemId,
        recordedAt = remediation.recordedAt,
    )
}
