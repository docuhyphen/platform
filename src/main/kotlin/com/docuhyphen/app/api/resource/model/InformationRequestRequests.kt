package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import kotlinx.serialization.Serializable
import java.util.UUID

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
