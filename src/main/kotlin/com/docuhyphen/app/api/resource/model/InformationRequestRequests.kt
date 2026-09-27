package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactVisibility
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestFindingSeverity
import com.docuhyphen.app.api.model.entity.InformationRequestRetestResult
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewVisibility
import com.docuhyphen.app.api.serializer.TimestampSerializer
import java.sql.Timestamp
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import kotlinx.serialization.Serializable
import java.util.UUID
import com.docuhyphen.app.api.model.entity.InformationRequestClockDueEffect
import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.entity.InformationRequestClockUrgency

@Serializable
data class CreateInformationRequestDraftRequest(
    @Serializable(with = UUIDSerializer::class) val exchangeId: UUID,
    val displayName: String,
    val description: String? = null,
    val configuration: InformationRequestTemplateConfigurationRequest,
    val gatesExchangeClosure: Boolean = true,
)

@Serializable
data class CancelInformationRequestRequest(
    val reasonCode: String? = null,
)

@Serializable
data class SupersedeInformationRequestRequest(
    @Serializable(with = UUIDSerializer::class) val supersededByRequestId: UUID,
    val reasonCode: String? = null,
)

/**
 * One sparse change to a single Requirement occurrence's response. [narrative] carries the new
 * value when set; [clearNarrative] is the explicit signal to blank an existing narrative, since an
 * absent [narrative] alone means "leave unchanged", not "clear". Setting both is refused by the
 * resource rather than left to guess which one wins.
 */
@Serializable
data class InformationRequestResponsePatchRequest(
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val disposition: InformationRequestResponseDisposition? = null,
    val narrative: String? = null,
    val clearNarrative: Boolean = false,
    val fieldValues: InformationRequestResponseFieldValuesPatchRequest? = null,
)

@Serializable
data class InformationRequestResponseFieldValuesPatchRequest(
    val etag: String? = null,
    val values: List<FieldValueEntry> = emptyList(),
)

@Serializable
data class PatchInformationRequestResponsesRequest(
    val patches: List<InformationRequestResponsePatchRequest>,
    val confirmedHiddenResponseClearRequirementIds: Set<@Serializable(with = UUIDSerializer::class) UUID> = emptySet(),
)

@Serializable
data class CreateInformationRequestGroupOccurrenceRequest(
    val groupKey: String,
    @Serializable(with = UUIDSerializer::class) val parentOccurrenceId: UUID? = null,
)

@Serializable
data class ReorderInformationRequestGroupOccurrencesRequest(
    val groupKey: String,
    @Serializable(with = UUIDSerializer::class) val parentOccurrenceId: UUID? = null,
    val occurrenceIds: List<@Serializable(with = UUIDSerializer::class) UUID>,
)

data class InformationRequestEvidenceAttributeForm(
    val issuer: String? = null,
    val jurisdiction: String? = null,
    val language: String? = null,
    val issuedOn: String? = null,
    val expiresOn: String? = null,
    val coverageStartsOn: String? = null,
    val coverageEndsOn: String? = null,
    val certificationReference: String? = null,
    val signatureReference: String? = null,
)

@Serializable
data class InformationRequestEvidenceStateChangeRequest(
    val reason: String? = null,
)

@Serializable
data class SubmitInformationRequestPackageRequest(
    val stageKey: String? = null,
)

@Serializable
data class WithdrawInformationRequestPackageRequest(
    val reasonCode: String? = null,
)

@Serializable
data class RecordInformationRequestAttestationRequest(
    val decision: InformationRequestAttestationDecision,
    val refusalReason: String? = null,
    val externalSignatureReference: String? = null,
    @Serializable(with = UUIDSerializer::class) val partyId: UUID? = null,
)

@Serializable
data class CreateInformationRequestSuccessorRequest(
    val kind: InformationRequestLineageKind,
    @Serializable(with = UUIDSerializer::class) val targetTemplateVersionId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val sourcePackageId: UUID? = null,
    val reasonCode: String? = null,
)

@Serializable
data class DefineInformationRequestRecurrenceRequest(
    val intervalUnit: InformationRequestRecurrenceUnit,
    val intervalCount: Int,
    val firstDueAt: String,
    val maximumOccurrences: Int? = null,
)

@Serializable
data class DefineInformationRequestRefreshRuleRequest(
    val requirementKey: String,
    val leadDays: Int,
)

@Serializable
data class AmendInformationRequestRequest(
    @Serializable(with = UUIDSerializer::class) val targetTemplateVersionId: UUID? = null,
    val configuration: InformationRequestTemplateConfigurationRequest? = null,
    val reasonCode: String? = null,
)

@Serializable
data class AssignInformationRequestReviewerRequest(
    val stageKey: String,
    @Serializable(with = UUIDSerializer::class) val reviewerPartyId: UUID,
    @Serializable(with = TimestampSerializer::class) val dueAt: Timestamp? = null,
)

@Serializable
data class ChangeInformationRequestReviewAssignmentRequest(
    val reasonCode: String? = null,
    val narrative: String? = null,
    @Serializable(with = UUIDSerializer::class) val delegatePartyId: UUID? = null,
)

@Serializable
data class InformationRequestReviewWorksheetEntryRequest(
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    val outcome: InformationRequestReviewOutcome? = null,
    val narrative: String? = null,
    val clear: Boolean = false,
)

@Serializable
data class SaveInformationRequestReviewWorksheetRequest(
    val entries: List<InformationRequestReviewWorksheetEntryRequest> = emptyList(),
)

@Serializable
data class OverrideInformationRequestReviewItemRequest(
    val stageKey: String,
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    val outcome: InformationRequestReviewOutcome,
    val narrative: String,
)

@Serializable
data class RecordInformationRequestReviewFindingRequest(
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    @Serializable(with = UUIDSerializer::class) val evidenceVersionId: UUID? = null,
    val reasonCode: String,
    val narrative: String,
    val severity: InformationRequestFindingSeverity,
    val visibility: InformationRequestReviewVisibility,
    val correctionScope: InformationRequestFindingCorrectionScope = InformationRequestFindingCorrectionScope.NONE,
    @Serializable(with = UUIDSerializer::class) val retestsFindingId: UUID? = null,
    val retestResult: InformationRequestRetestResult? = null,
)

@Serializable
data class RecordInformationRequestReviewCommentRequest(
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    @Serializable(with = UUIDSerializer::class) val findingId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val replyToCommentId: UUID? = null,
    val visibility: InformationRequestReviewVisibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
    val body: String,
)

@Serializable
data class ReopenInformationRequestReviewRequest(
    val reason: String,
)

@Serializable
data class PromoteInformationRequestAcceptedFactRequest(
    @Serializable(with = UUIDSerializer::class) val packageId: UUID,
    @Serializable(with = UUIDSerializer::class) val submissionItemId: UUID,
    val purposeKey: String,
    val visibility: InformationRequestAcceptedFactVisibility = InformationRequestAcceptedFactVisibility.REQUESTING_SIDE,
    @Serializable(with = TimestampSerializer::class) val validFrom: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val validTo: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    @Serializable(with = UUIDSerializer::class) val supersedesFactId: UUID? = null,
)

@Serializable
data class RevokeInformationRequestAcceptedFactRequest(
    val reasonCode: String,
    val narrative: String? = null,
)

@Serializable
data class RecordInformationRequestBusinessDecisionRequest(
    val owningProcessKey: String,
    val outcomeCode: String,
    val reasonReference: String? = null,
    val externalReference: String? = null,
    val kind: InformationRequestBusinessDecisionKind = InformationRequestBusinessDecisionKind.ORIGINAL,
    @Serializable(with = UUIDSerializer::class) val priorDecisionId: UUID? = null,
    @Serializable(with = TimestampSerializer::class) val decidedAt: Timestamp,
)

@Serializable
data class ChangeInformationRequestCompletionGateRequest(
    val gatesExchangeClosure: Boolean,
)

@Serializable
data class InformationRequestWorkingPeriodRequest(
    val dayOfWeek: String,
    val startMinute: Int,
    val endMinute: Int,
)

@Serializable
data class InformationRequestClockPolicyDefinitionRequest(
    val clockType: InformationRequestClockType,
    val businessTimezone: String,
    val workingPeriods: List<InformationRequestWorkingPeriodRequest> = emptyList(),
    val holidays: List<String> = emptyList(),
    val standardDurationMinutes: Int,
    val urgentDurationMinutes: Int,
    val reminderMinutesBeforeDue: List<Int> = emptyList(),
    val escalationAfterMinutes: Int? = null,
    val dueEffect: InformationRequestClockDueEffect =
        InformationRequestClockDueEffect.MARK_OVERDUE,
    @Serializable(with = UUIDSerializer::class) val reminderCommunicationId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val overdueCommunicationId: UUID? = null,
)

@Serializable
data class DefineInformationRequestClockPolicyRequest(
    val policyKey: String,
    val displayName: String,
    val definition: InformationRequestClockPolicyDefinitionRequest,
)

@Serializable
data class StartInformationRequestClockRequest(
    val clockKey: String,
    @Serializable(with = UUIDSerializer::class) val policyVersionId: UUID,
    val urgency: InformationRequestClockUrgency =
        InformationRequestClockUrgency.STANDARD,
    @Serializable(with = TimestampSerializer::class) val receivedAt: Timestamp? = null,
)

@Serializable
data class ChangeInformationRequestClockRequest(
    val reasonCode: String,
    val extensionMinutes: Int? = null,
)
