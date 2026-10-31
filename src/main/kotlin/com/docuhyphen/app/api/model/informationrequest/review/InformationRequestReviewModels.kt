package com.docuhyphen.app.api.model.informationrequest.review

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceVersionRecord
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionPackageView
import java.time.Instant
import java.util.*

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
    val assignmentId: UUID? = null,
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
    val promptsByBinding: Map<UUID, String> = emptyMap(),
)
{
    fun item(itemId: UUID): InformationRequestSubmissionItem? = submission.items.firstOrNull { it.id == itemId }
}

data class InformationRequestReturnedEvidence(
    val correctionItemId: UUID,
    val evidenceArtifactId: UUID,
    val evidenceVersionId: UUID,
    val versionNumber: Int,
    val declaredFileName: String?,
)

data class InformationRequestReviewCorrectionView(
    val correction: InformationRequestCorrection,
    val items: List<InformationRequestCorrectionItem>,
    val evidence: List<InformationRequestReturnedEvidence>,
)

data class InformationRequestReviewDelegationCandidate(
    val partyId: UUID,
    val label: String,
)

data class InformationRequestReadableReview(
    val snapshot: InformationRequestReviewSnapshot,
    val contentVisibleItemIds: Set<UUID>,
    val fieldValues: Map<UUID, FieldValueRevisionValue>,
    val evidenceVersions: Map<UUID, InformationRequestEvidenceVersionRecord>,
    val findings: List<InformationRequestReviewFinding>,
    val comments: List<InformationRequestReviewComment>,
    val correction: InformationRequestReviewCorrectionView?,
    val remediations: List<InformationRequestReviewRemediation>,
    val earlierFindings: List<InformationRequestReviewFinding>,
    val earlierRemediations: List<InformationRequestReviewRemediation>,
    val callerAssignmentIds: Set<UUID>,
    val drafts: Map<UUID, List<InformationRequestReviewDraftItem>>,
    val canManage: Boolean,
    val canReconsider: Boolean,
    val openingReasonVisible: Boolean = false,
    val itemsAwaitingStepUp: Int = 0,
    val delegationCandidates: Map<UUID, List<InformationRequestReviewDelegationCandidate>> = emptyMap(),
    val canComment: Boolean = false,
    val waiverPermittedRequirementIds: Set<UUID> = emptySet(),
    val canRequestSupplement: Boolean = false,
    val requestETag: String? = null,
) {
    val contentVisibleRequirementIds: Set<UUID>
        get() = snapshot.submission.items
            .filter { it.id in contentVisibleItemIds }
            .map { it.informationRequestRequirementId }
            .toSet()
}

data class InformationRequestReviewReopenFacts(
    val latestReviewIdByPackage: Map<UUID, UUID>,
    val currentPackageIds: Set<UUID>,
    val correctionByReview: Map<UUID, InformationRequestCorrection>,
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
    val openingReasonVisible: Boolean = false,
    val visibleItems: List<InformationRequestReviewResultItem> = emptyList(),
)

data class InformationRequestReviewResultItem(
    val submissionItemId: UUID,
    val requirementId: UUID,
)

data class InformationRequestReviewQueueEntry(
    val assignment: InformationRequestReviewAssignment,
    val review: InformationRequestReview,
    val request: InformationRequest,
    val packageNumber: Int,
    val requestTitle: String,
    val exchangeName: String?,
    val reviewStageTitle: String,
    val stageKey: String?,
    val itemCount: Int,
    val executionStanding: InformationRequestExecutionStanding,
)
