package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestCorrection
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionEvidence
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionItem
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestFindingSeverity
import com.docuhyphen.app.api.model.entity.InformationRequestRetestResult
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignment
import com.docuhyphen.app.api.model.entity.InformationRequestReviewComment
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecision
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDraftItem
import com.docuhyphen.app.api.model.entity.InformationRequestReviewFinding
import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewRemediation
import com.docuhyphen.app.api.model.entity.InformationRequestReviewVisibility
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import java.time.Instant
import java.util.UUID

data class AssignInformationRequestReviewerCommand(
    val requestId: UUID,
    val reviewId: UUID,
    val stageKey: String,
    val reviewerPartyId: UUID,
    val dueAt: Instant? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

enum class InformationRequestReviewAssignmentChange
{
    RECUSAL,
    DELEGATION,
    REVOCATION,
}

data class ChangeInformationRequestReviewAssignmentCommand(
    val requestId: UUID,
    val reviewId: UUID,
    val assignmentId: UUID,
    val change: InformationRequestReviewAssignmentChange,
    val reasonCode: String? = null,
    val narrative: String? = null,
    val delegatePartyId: UUID? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestReviewDraftPatch(
    val submissionItemId: UUID,
    val outcome: InformationRequestReviewOutcome? = null,
    val narrative: String? = null,
    val clear: Boolean = false,
)

data class SaveInformationRequestReviewDraftCommand(
    val requestId: UUID,
    val reviewId: UUID,
    val assignmentId: UUID,
    val patches: List<InformationRequestReviewDraftPatch>,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
)

data class RecordInformationRequestReviewDecisionsCommand(
    val requestId: UUID,
    val reviewId: UUID,
    val assignmentId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class OverrideInformationRequestReviewItemCommand(
    val requestId: UUID,
    val reviewId: UUID,
    val stageKey: String,
    val submissionItemId: UUID,
    val outcome: InformationRequestReviewOutcome,
    val narrative: String,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class RecordInformationRequestReviewFindingCommand(
    val requestId: UUID,
    val reviewId: UUID,
    val submissionItemId: UUID,
    val evidenceVersionId: UUID? = null,
    val reasonCode: String,
    val narrative: String,
    val severity: InformationRequestFindingSeverity,
    val visibility: InformationRequestReviewVisibility,
    val correctionScope: InformationRequestFindingCorrectionScope,
    val retestsFindingId: UUID? = null,
    val retestResult: InformationRequestRetestResult? = null,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class RecordInformationRequestReviewCommentCommand(
    val requestId: UUID,
    val reviewId: UUID,
    val submissionItemId: UUID,
    val findingId: UUID? = null,
    val replyToCommentId: UUID? = null,
    val visibility: InformationRequestReviewVisibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
    val body: String,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class ReopenInformationRequestReviewCommand(
    val requestId: UUID,
    val reviewId: UUID,
    val kind: InformationRequestReviewKind,
    val reason: String,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestReviewCommandResult(
    val request: InformationRequest,
    val review: InformationRequestReview,
    val reviewETag: String,
    val responseETag: String,
    val assignment: InformationRequestReviewAssignment? = null,
    val draftETag: String? = null,
    val finding: InformationRequestReviewFinding? = null,
    val comment: InformationRequestReviewComment? = null,
)

data class InformationRequestReviewDraftResult(
    val assignment: InformationRequestReviewAssignment,
    val items: List<InformationRequestReviewDraftItem>,
    val draftETag: String,
)

data class InformationRequestReviewSnapshot(
    val review: InformationRequestReview,
    val submission: InformationRequestSubmissionPackageView,
    val plan: InformationRequestReviewPlan,
    val reviewedItems: List<InformationRequestSubmissionItem>,
    val coverage: Map<String, List<UUID>>,
    val assignments: List<InformationRequestReviewAssignment>,
    val decisions: List<InformationRequestReviewDecision>,
    val findings: List<InformationRequestReviewFinding>,
    val standing: InformationRequestReviewResult,
)
{
    fun item(itemId: UUID): InformationRequestSubmissionItem? = submission.items.firstOrNull { it.id == itemId }
}

data class InformationRequestReviewCorrectionView(
    val correction: InformationRequestCorrection,
    val items: List<InformationRequestCorrectionItem>,
    val evidence: List<InformationRequestCorrectionEvidence>,
)

data class InformationRequestReadableReview(
    val snapshot: InformationRequestReviewSnapshot,
    val contentVisibleItemIds: Set<UUID>,
    val fieldValues: Map<UUID, FieldValueRevisionValue>,
    val comments: List<InformationRequestReviewComment>,
    val correction: InformationRequestReviewCorrectionView?,
    val remediations: List<InformationRequestReviewRemediation>,
    val callerAssignmentIds: Set<UUID>,
    val drafts: Map<UUID, List<InformationRequestReviewDraftItem>>,
    val canManage: Boolean,
)

data class InformationRequestRespondentReviewResult(
    val review: InformationRequestReview,
    val packageNumber: Int,
    val stageKey: String?,
    val visibleFindings: List<InformationRequestReviewFinding>,
    val visibleComments: List<InformationRequestReviewComment>,
    val correction: InformationRequestReviewCorrectionView?,
    val visibleRequirementIds: Set<UUID>,
    val remediations: List<InformationRequestReviewRemediation>,
    val canAppeal: Boolean,
    val canComment: Boolean,
)

data class InformationRequestReviewQueueEntry(
    val assignment: InformationRequestReviewAssignment,
    val review: InformationRequestReview,
    val request: InformationRequest,
    val packageNumber: Int,
    val stageKey: String?,
    val itemCount: Int,
)
