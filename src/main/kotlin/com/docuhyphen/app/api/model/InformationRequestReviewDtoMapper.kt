package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.RevisionETag
import java.util.*

object InformationRequestReviewDtoMapper
{
    fun toDto(readable: InformationRequestReadableReview, caller: PrincipalRef): InformationRequestReviewDto
    {
        val snapshot = readable.snapshot
        val view = snapshot.submission
        val reviewedIds = snapshot.reviewedItems.map { it.id }.toSet()
        val evidenceByItem = view.evidence.groupBy { it.itemId }
        return InformationRequestReviewDto(
            review = summary(
                snapshot.review,
                view.submissionPackage.packageNumber,
                view.submissionPackage.stageKey,
                readable.openingReasonVisible,
            ),
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
                    items = standing.items.map {
                        InformationRequestReviewItemStandingDto(
                            it.itemId,
                            it.standing,
                            it.outcome
                        )
                    },
                )
            },
            items = view.items.map { item ->
                val visible = item.id in readable.contentVisibleItemIds
                val fieldValue = item.fieldValueRevisionId?.let(readable.fieldValues::get)
                InformationRequestReviewItemDto(
                    submissionItemId = item.id,
                    requirementId = item.informationRequestRequirementId,
                    requirementKey = item.requirementKey,
                    prompt = snapshot.promptsByBinding[item.templateBindingId],
                    requirementType = item.requirementType,
                    occurrencePath = item.occurrencePath,
                    reviewed = item.id in reviewedIds,
                    finalOutcome = snapshot.standing.itemOutcomes[item.id],
                    contentVisible = visible,
                    waiverPermitted = item.informationRequestRequirementId in readable.waiverPermittedRequirementIds,
                    disposition = item.disposition.takeIf { visible },
                    narrative = item.narrative.takeIf { visible },
                    fieldValue = fieldValue?.value.takeIf { visible },
                    evidence = if (visible) evidenceByItem[item.id].orEmpty().map { member ->
                        InformationRequestSubmissionDtoMapper.toDto(
                            member,
                            readable.evidenceVersions[member.evidenceVersionId]
                        )
                    }
                    else emptyList(),
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
                    changeNarrative = assignment.changeNarrative,
                    changedAt = assignment.changedAt,
                    decidedAt = assignment.decidedAt,
                    callerIsReviewer = assignment.id in readable.callerAssignmentIds,
                    delegationCandidates = readable.delegationCandidates[assignment.id].orEmpty().map { candidate ->
                        InformationRequestReviewDelegationCandidateDto(candidate.partyId, candidate.label)
                    },
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
                    narrative = decision.narrative.takeIf { decision.submissionItemId in readable.contentVisibleItemIds },
                    decidedAt = decision.decidedAt,
                    decidedByCaller = decision.decidedByPrincipalKind == caller.kind && decision.decidedByPrincipalId == caller.id,
                )
            },
            findings = readable.findings.map { toDto(it, caller, revealsAssignment = true) },
            comments = readable.comments.map { toDto(it, caller) },
            correction = readable.correction?.let { toDto(it, readable.contentVisibleRequirementIds) },
            remediations = readable.remediations.map(::toDto),
            earlierFindings = readable.earlierFindings.map { toDto(it, caller, revealsAssignment = true) },
            earlierRemediations = readable.earlierRemediations.map(::toDto),
            worksheets = readable.drafts.map { (assignmentId, entries) ->
                val assignment = snapshot.assignments.first { it.id == assignmentId }
                worksheet(assignment, RevisionETag.of(assignment.id, assignment.draftRevision), entries)
            },
            canManage = readable.canManage,
            canReconsider = readable.canReconsider,
            canComment = readable.canComment,
            itemsAwaitingStepUp = readable.itemsAwaitingStepUp,
            canRequestSupplement = readable.canRequestSupplement,
            requestETag = readable.requestETag,
        )
    }

    fun toDto(
        result: InformationRequestRespondentReviewResult,
        caller: PrincipalRef
    ): InformationRequestRespondentReviewDto =
        InformationRequestRespondentReviewDto(
            review = summary(result.review, result.packageNumber, result.stageKey, result.openingReasonVisible),
            findings = result.visibleFindings.map { toDto(it, caller, revealsAssignment = false) },
            comments = result.visibleComments.map { toDto(it, caller) },
            items = result.visibleItems.map {
                InformationRequestReviewResultItemDto(
                    it.submissionItemId,
                    it.requirementId
                )
            },
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
            requestTitle = entry.requestTitle,
            exchangeName = entry.exchangeName,
            reviewStageTitle = entry.reviewStageTitle,
            packageNumber = entry.packageNumber,
            submissionStageKey = entry.stageKey,
            reviewState = entry.review.state,
            itemCount = entry.itemCount,
            dueAt = entry.assignment.dueAt,
            assignedAt = entry.assignment.assignedAt,
            executionStanding = InformationRequestExecutionStandingDtoMapper.toDto(entry.executionStanding),
        )

    fun toDto(
        result: InformationRequestReviewCommandResult,
        packageNumber: Int,
        stageKey: String?,
        openingReasonVisible: Boolean,
    ): InformationRequestReviewCommandResultDto =
        InformationRequestReviewCommandResultDto(
            requestState = result.request.state,
            responseETag = result.responseETag,
            review = summary(
                result.review,
                packageNumber,
                stageKey,
                openingReasonVisible,
            ),
            assignmentId = result.assignment?.id,
            draftETag = result.draftETag,
            findingId = result.finding?.id,
            commentId = result.comment?.id,
        )

    fun toDto(result: InformationRequestReviewDraftResult): InformationRequestReviewWorksheetDto =
        worksheet(result.assignment, result.draftETag, result.items)

    private fun worksheet(
        assignment: InformationRequestReviewAssignment,
        draftETag: String,
        entries: List<InformationRequestReviewDraftItem>,
    ) =
        InformationRequestReviewWorksheetDto(
            assignmentId = assignment.id,
            draftETag = draftETag,
            draftRevision = assignment.draftRevision,
            entries = entries.map {
                InformationRequestReviewWorksheetEntryDto(
                    it.submissionItemId,
                    it.outcome,
                    it.narrative
                )
            },
        )

    private fun summary(
        review: InformationRequestReview,
        packageNumber: Int,
        stageKey: String?,
        revealsOpeningReason: Boolean
    ) =
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
            openingReason = review.openingReason.takeIf { revealsOpeningReason },
            openedAt = review.openedAt,
            settledAt = review.settledAt,
            reviewETag = RevisionETag.of(review.id, review.reviewRevision),
        )

    private fun toDto(finding: InformationRequestReviewFinding, caller: PrincipalRef, revealsAssignment: Boolean) =
        InformationRequestReviewFindingDto(
            id = finding.id,
            reviewId = finding.reviewId,
            submissionItemId = finding.submissionItemId,
            requirementId = finding.requirementId,
            evidenceVersionId = finding.evidenceVersionId,
            assignmentId = finding.assignmentId.takeIf { revealsAssignment },
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

    private fun toDto(comment: InformationRequestReviewComment, caller: PrincipalRef) =
        InformationRequestReviewCommentDto(
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

    private fun toDto(
        view: InformationRequestReviewCorrectionView,
        visibleRequirementIds: Set<UUID>?
    ): InformationRequestCorrectionDto
    {
        val visibleItems =
            view.items.filter { visibleRequirementIds == null || it.requirementId in visibleRequirementIds }
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
            evidence = view.evidence.filter { it.correctionItemId in visibleItemIds }.map { returned ->
                InformationRequestCorrectionEvidenceDto(
                    artifactId = returned.evidenceArtifactId,
                    evidenceVersionId = returned.evidenceVersionId,
                    versionNumber = returned.versionNumber,
                    declaredFileName = returned.declaredFileName,
                )
            },
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
