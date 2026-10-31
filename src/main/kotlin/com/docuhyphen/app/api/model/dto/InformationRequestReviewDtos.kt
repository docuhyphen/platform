package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewItemStanding
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStageState
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.util.*

@Serializable
data class InformationRequestReviewSummaryDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val informationRequestId: UUID,
    @Serializable(with = UUIDSerializer::class) val packageId: UUID,
    val packageNumber: Int,
    val stageKey: String? = null,
    val reviewNumber: Int,
    val kind: InformationRequestReviewKind,
    @Serializable(with = UUIDSerializer::class) val priorReviewId: UUID? = null,
    val state: InformationRequestReviewState,
    val openingReason: String? = null,
    @Serializable(with = TimestampSerializer::class) val openedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val settledAt: Timestamp? = null,
    val reviewETag: String,
)

@Serializable
data class InformationRequestReviewDto(
    val review: InformationRequestReviewSummaryDto,
    val reviewStageOrdering: InformationRequestReviewStageOrdering,
    val stages: List<InformationRequestReviewStageStandingDto> = emptyList(),
    val items: List<InformationRequestReviewItemDto> = emptyList(),
    val assignments: List<InformationRequestReviewAssignmentDto> = emptyList(),
    val decisions: List<InformationRequestReviewDecisionDto> = emptyList(),
    val findings: List<InformationRequestReviewFindingDto> = emptyList(),
    val comments: List<InformationRequestReviewCommentDto> = emptyList(),
    val correction: InformationRequestCorrectionDto? = null,
    val remediations: List<InformationRequestRemediationDto> = emptyList(),
    val earlierFindings: List<InformationRequestReviewFindingDto> = emptyList(),
    val earlierRemediations: List<InformationRequestRemediationDto> = emptyList(),
    val worksheets: List<InformationRequestReviewWorksheetDto> = emptyList(),
    val canManage: Boolean,
    val canReconsider: Boolean,
    val canComment: Boolean = false,
    val itemsAwaitingStepUp: Int = 0,
    val canRequestSupplement: Boolean = false,
    val requestETag: String? = null,
)

@Serializable
data class InformationRequestReviewStageStandingDto(
    val stageKey: String,
    val title: String,
    val position: Int,
    val aggregation: InformationRequestReviewAggregation,
    val quorumCount: Int? = null,
    val minimumReviewerCount: Int,
    val tieResolution: InformationRequestReviewTieResolution,
    val overridePermitted: Boolean,
    val excludesResponseParties: Boolean,
    val excludesPriorReviewers: Boolean,
    val state: InformationRequestReviewStageState,
    val items: List<InformationRequestReviewItemStandingDto> = emptyList(),
)

@Serializable
data class InformationRequestReviewItemStandingDto(
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    val standing: InformationRequestReviewItemStanding,
    val outcome: InformationRequestReviewOutcome? = null,
)

@Serializable
data class InformationRequestReviewItemDto(
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val requirementKey: String,
    val prompt: String? = null,
    val requirementType: InformationRequestRequirementType,
    val occurrencePath: String,
    val reviewed: Boolean,
    val finalOutcome: InformationRequestReviewOutcome? = null,
    val contentVisible: Boolean,
    val waiverPermitted: Boolean = false,
    val disposition: InformationRequestResponseDisposition? = null,
    val narrative: String? = null,
    val fieldValue: JsonElement? = null,
    val evidence: List<InformationRequestSubmissionEvidenceDto> = emptyList(),
)

@Serializable
data class InformationRequestReviewAssignmentDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val stageKey: String,
    @Serializable(with = UUIDSerializer::class) val reviewerPartyId: UUID,
    val state: InformationRequestReviewAssignmentState,
    @Serializable(with = TimestampSerializer::class) val dueAt: Timestamp? = null,
    @Serializable(with = UUIDSerializer::class) val delegatedFromAssignmentId: UUID? = null,
    @Serializable(with = TimestampSerializer::class) val assignedAt: Timestamp,
    val changeReasonCode: String? = null,
    val changeNarrative: String? = null,
    @Serializable(with = TimestampSerializer::class) val changedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val decidedAt: Timestamp? = null,
    val callerIsReviewer: Boolean,
    val delegationCandidates: List<InformationRequestReviewDelegationCandidateDto> = emptyList(),
)

@Serializable
data class InformationRequestReviewDelegationCandidateDto(
    @Serializable(with = UUIDSerializer::class) val partyId: UUID,
    val label: String,
)

@Serializable
data class InformationRequestReviewDecisionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val stageKey: String,
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    val kind: InformationRequestReviewDecisionKind,
    @Serializable(with = UUIDSerializer::class) val assignmentId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val carriedFromDecisionId: UUID? = null,
    val outcome: InformationRequestReviewOutcome,
    val narrative: String? = null,
    @Serializable(with = TimestampSerializer::class) val decidedAt: Timestamp,
    val decidedByCaller: Boolean,
)

@Serializable
data class InformationRequestReviewFindingDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val reviewId: UUID,
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    @Serializable(with = UUIDSerializer::class) val evidenceVersionId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val assignmentId: UUID? = null,
    val reasonCode: String,
    val narrative: String,
    val severity: InformationRequestFindingSeverity,
    val visibility: InformationRequestReviewVisibility,
    val correctionScope: InformationRequestFindingCorrectionScope,
    @Serializable(with = UUIDSerializer::class) val retestsFindingId: UUID? = null,
    val retestResult: InformationRequestRetestResult? = null,
    @Serializable(with = TimestampSerializer::class) val recordedAt: Timestamp,
    val recordedByCaller: Boolean,
)

@Serializable
data class InformationRequestReviewCommentDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    @Serializable(with = UUIDSerializer::class) val findingId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val replyToCommentId: UUID? = null,
    val authorRole: InformationRequestReviewCommentRole,
    val visibility: InformationRequestReviewVisibility,
    val body: String,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
    val authoredByCaller: Boolean,
)

@Serializable
data class InformationRequestCorrectionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val reviewId: UUID,
    @Serializable(with = UUIDSerializer::class) val packageId: UUID,
    val state: InformationRequestCorrectionState,
    @Serializable(with = TimestampSerializer::class) val openedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val closedAt: Timestamp? = null,
    @Serializable(with = UUIDSerializer::class) val resubmittedPackageId: UUID? = null,
    val requirementIds: List<@Serializable(with = UUIDSerializer::class) UUID> = emptyList(),
    val evidence: List<InformationRequestCorrectionEvidenceDto> = emptyList(),
    val undisclosedItemCount: Int = 0,
)

@Serializable
data class InformationRequestCorrectionEvidenceDto(
    @Serializable(with = UUIDSerializer::class) val artifactId: UUID,
    @Serializable(with = UUIDSerializer::class) val evidenceVersionId: UUID,
    val versionNumber: Int,
    val declaredFileName: String? = null,
)

@Serializable
data class InformationRequestRemediationDto(
    @Serializable(with = UUIDSerializer::class) val findingId: UUID,
    @Serializable(with = UUIDSerializer::class) val remediatedByPackageId: UUID,
    @Serializable(with = UUIDSerializer::class) val remediatedByItemId: UUID,
    @Serializable(with = TimestampSerializer::class) val recordedAt: Timestamp,
)

@Serializable
data class InformationRequestReviewWorksheetEntryDto(
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    val outcome: InformationRequestReviewOutcome,
    val narrative: String? = null,
)

@Serializable
data class InformationRequestReviewWorksheetDto(
    @Serializable(with = UUIDSerializer::class) val assignmentId: UUID,
    val draftETag: String,
    val draftRevision: Long,
    val entries: List<InformationRequestReviewWorksheetEntryDto> = emptyList(),
)

@Serializable
data class InformationRequestRespondentReviewDto(
    val review: InformationRequestReviewSummaryDto,
    val findings: List<InformationRequestReviewFindingDto> = emptyList(),
    val comments: List<InformationRequestReviewCommentDto> = emptyList(),
    val items: List<InformationRequestReviewResultItemDto> = emptyList(),
    val correction: InformationRequestCorrectionDto? = null,
    val remediations: List<InformationRequestRemediationDto> = emptyList(),
    val canAppeal: Boolean,
    val canComment: Boolean,
)

@Serializable
data class InformationRequestReviewResultItemDto(
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
)

@Serializable
data class InformationRequestReviewQueueEntryDto(
    @Serializable(with = UUIDSerializer::class) val assignmentId: UUID,
    @Serializable(with = UUIDSerializer::class) val reviewId: UUID,
    @Serializable(with = UUIDSerializer::class) val informationRequestId: UUID,
    @Serializable(with = UUIDSerializer::class) val exchangeId: UUID,
    val reviewStageKey: String,
    val requestTitle: String,
    val exchangeName: String? = null,
    val reviewStageTitle: String,
    val packageNumber: Int,
    val submissionStageKey: String? = null,
    val reviewState: InformationRequestReviewState,
    val itemCount: Int,
    @Serializable(with = TimestampSerializer::class) val dueAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val assignedAt: Timestamp,
    val executionStanding: InformationRequestExecutionStandingDto,
)

@Serializable
data class InformationRequestReviewCommandResultDto(
    val requestState: InformationRequestState,
    val responseETag: String,
    val review: InformationRequestReviewSummaryDto,
    @Serializable(with = UUIDSerializer::class) val assignmentId: UUID? = null,
    val draftETag: String? = null,
    @Serializable(with = UUIDSerializer::class) val findingId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val commentId: UUID? = null,
)
