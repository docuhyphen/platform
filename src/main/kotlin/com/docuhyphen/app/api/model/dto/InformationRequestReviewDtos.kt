package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionState
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestFindingSeverity
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestRetestResult
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAggregation
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignmentState
import com.docuhyphen.app.api.model.entity.InformationRequestReviewCommentRole
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewStageOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.entity.InformationRequestReviewTieResolution
import com.docuhyphen.app.api.model.entity.InformationRequestReviewVisibility
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewItemStanding
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStageState
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.util.UUID

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
    val worksheets: List<InformationRequestReviewWorksheetDto> = emptyList(),
    val canManage: Boolean,
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
    val requirementType: InformationRequestRequirementType,
    val occurrencePath: String,
    val reviewed: Boolean,
    val finalOutcome: InformationRequestReviewOutcome? = null,
    val contentVisible: Boolean,
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
    @Serializable(with = TimestampSerializer::class) val changedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val decidedAt: Timestamp? = null,
    val callerIsReviewer: Boolean,
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
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    @Serializable(with = UUIDSerializer::class) val evidenceVersionId: UUID? = null,
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
    val evidenceVersionIds: List<@Serializable(with = UUIDSerializer::class) UUID> = emptyList(),
    val undisclosedItemCount: Int = 0,
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
    val entries: List<InformationRequestReviewWorksheetEntryDto> = emptyList(),
)

@Serializable
data class InformationRequestRespondentReviewDto(
    val review: InformationRequestReviewSummaryDto,
    val findings: List<InformationRequestReviewFindingDto> = emptyList(),
    val comments: List<InformationRequestReviewCommentDto> = emptyList(),
    val correction: InformationRequestCorrectionDto? = null,
    val remediations: List<InformationRequestRemediationDto> = emptyList(),
    val canAppeal: Boolean,
    val canComment: Boolean,
)

@Serializable
data class InformationRequestReviewQueueEntryDto(
    @Serializable(with = UUIDSerializer::class) val assignmentId: UUID,
    @Serializable(with = UUIDSerializer::class) val reviewId: UUID,
    @Serializable(with = UUIDSerializer::class) val informationRequestId: UUID,
    @Serializable(with = UUIDSerializer::class) val exchangeId: UUID,
    val reviewStageKey: String,
    val packageNumber: Int,
    val submissionStageKey: String? = null,
    val reviewState: InformationRequestReviewState,
    val itemCount: Int,
    @Serializable(with = TimestampSerializer::class) val dueAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val assignedAt: Timestamp,
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
