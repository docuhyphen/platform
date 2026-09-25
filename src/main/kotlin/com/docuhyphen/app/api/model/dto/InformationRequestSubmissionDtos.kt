package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestAuthenticationStrength
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestExternalSignatureReferencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionStageOrdering
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionProblemCode
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestSubmissionPackageDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val informationRequestId: UUID,
    val packageNumber: Int,
    val stageKey: String? = null,
    @Serializable(with = UUIDSerializer::class) val templateVersionId: UUID,
    @Serializable(with = UUIDSerializer::class) val schemaVersionId: UUID? = null,
    val contentHash: String,
    val manifestHash: String,
    val reviewRequired: Boolean,
    val completesRequest: Boolean,
    @Serializable(with = UUIDSerializer::class) val previousPackageId: UUID? = null,
    @Serializable(with = TimestampSerializer::class) val submittedAt: Timestamp,
    val submittedByCaller: Boolean,
    val withdrawn: Boolean,
    @Serializable(with = TimestampSerializer::class) val withdrawnAt: Timestamp? = null,
    val withdrawalReasonCode: String? = null,
    val items: List<InformationRequestSubmissionItemDto> = emptyList(),
    val attestations: List<InformationRequestSubmissionAttestationDto> = emptyList(),
    val supportingEvidenceLinks: List<InformationRequestSupportingEvidenceLinkDto> = emptyList(),
    val undisclosedItemCount: Int = 0,
)

@Serializable
data class InformationRequestSubmissionItemDto(
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val requirementKey: String,
    val requirementType: InformationRequestRequirementType,
    val occurrencePath: String,
    val completenessState: InformationRequestCompletenessItemState,
    val disposition: InformationRequestResponseDisposition,
    val narrative: String? = null,
    val fieldValue: JsonElement? = null,
    val fieldValueCleared: Boolean = false,
    val evidenceState: String? = null,
    val attestationState: String? = null,
    val evidence: List<InformationRequestSubmissionEvidenceDto> = emptyList(),
)

@Serializable
data class InformationRequestSubmissionEvidenceDto(
    @Serializable(with = UUIDSerializer::class) val artifactId: UUID,
    @Serializable(with = UUIDSerializer::class) val evidenceVersionId: UUID,
    val versionNumber: Int,
    @Serializable(with = UUIDSerializer::class) val documentVersionId: UUID? = null,
    val contentHashAlgorithm: String? = null,
    val contentHash: String? = null,
    val contentLength: Long? = null,
    val conformance: String,
)

@Serializable
data class InformationRequestSubmissionAttestationDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val stageKey: String? = null,
    val partyRole: InformationRequestContributorRole,
    val decision: InformationRequestAttestationDecision,
    val refusalReason: String? = null,
    val authenticationStrength: InformationRequestAuthenticationStrength,
    val externalSignatureReference: String? = null,
    @Serializable(with = TimestampSerializer::class) val attestedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    val attestedByCaller: Boolean,
    val madeUnderDelegatedAuthority: Boolean,
)

@Serializable
data class InformationRequestSubmissionPreviewDto(
    @Serializable(with = UUIDSerializer::class) val informationRequestId: UUID,
    val stageKey: String? = null,
    val submissionMode: InformationRequestSubmissionMode,
    val submissionStageOrdering: InformationRequestSubmissionStageOrdering,
    val submissionETag: String,
    val ready: Boolean,
    val problems: List<InformationRequestSubmissionProblemDto> = emptyList(),
    val undisclosedProblemCount: Int = 0,
    val attestations: List<InformationRequestAttestationStatusDto> = emptyList(),
    val stages: List<InformationRequestSubmissionStageDto> = emptyList(),
    val canSubmit: Boolean,
    val packages: List<InformationRequestSubmissionPackageDto> = emptyList(),
)

@Serializable
data class InformationRequestSubmissionProblemDto(
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val requirementKey: String,
    val occurrencePath: String,
    val code: InformationRequestSubmissionProblemCode,
)

@Serializable
data class InformationRequestAttestationStatusDto(
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val requirementKey: String,
    val prompt: String,
    val state: InformationRequestAttestationState,
    val requiredRoles: List<InformationRequestContributorRole>,
    val missingRoles: List<InformationRequestContributorRole>,
    val ordering: InformationRequestAttestationOrdering,
    val assentCount: Int,
    val requiredAssentCount: Int,
    val minimumAuthenticationStrength: InformationRequestAuthenticationStrength,
    val externalSignatureReference: InformationRequestExternalSignatureReferencePolicy,
    val validityHours: Int? = null,
    val callerCanAttest: Boolean,
    val callerDecision: InformationRequestAttestationDecision? = null,
    val attestations: List<InformationRequestSubmissionAttestationDto> = emptyList(),
)

@Serializable
data class InformationRequestSubmissionStageDto(
    val stageKey: String,
    @Serializable(with = UUIDSerializer::class) val submittedPackageId: UUID? = null,
    val submittedPackageNumber: Int? = null,
    val submitted: Boolean,
)

@Serializable
data class InformationRequestSubmissionRefusalDto(
    val errorMessage: String,
    val reasonCode: String,
    val problems: List<InformationRequestSubmissionProblemDto> = emptyList(),
    val undisclosedProblemCount: Int = 0,
)

@Serializable
data class InformationRequestSubmissionAttestationResultDto(
    val attestation: InformationRequestSubmissionAttestationDto,
    val state: InformationRequestAttestationState? = null,
    val submissionETag: String,
)

@Serializable
data class InformationRequestSubmissionResultDto(
    val requestState: InformationRequestState,
    val requestETag: String,
    val responseETag: String,
    val submission: InformationRequestSubmissionPackageDto,
)
