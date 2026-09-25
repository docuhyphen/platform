package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceSourceKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceConformance
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFindingCode
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestEvidenceVersionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val versionNumber: Int,
    val sourceKind: InformationRequestEvidenceSourceKind,
    val declaredFileName: String? = null,
    val declaredMediaType: String? = null,
    val contentLength: Long? = null,
    val contentHashAlgorithm: DocumentVersionContentHashAlgorithm? = null,
    val contentHash: String? = null,
    val contentVerification: DocumentVersionContentVerification? = null,
    val externalReferenceType: String? = null,
    val externalReferenceValue: String? = null,
    val issuer: String? = null,
    val jurisdiction: String? = null,
    val language: String? = null,
    val issuedOn: String? = null,
    val expiresOn: String? = null,
    val coverageStartsOn: String? = null,
    val coverageEndsOn: String? = null,
    val certificationReference: String? = null,
    val signatureReference: String? = null,
    val createdByCaller: Boolean,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
    val conformance: InformationRequestEvidenceConformance? = null,
    val findings: List<InformationRequestEvidenceFindingDto> = emptyList(),
)

@Serializable
data class InformationRequestEvidenceFindingDto(
    val code: InformationRequestEvidenceFindingCode,
    val blocking: Boolean,
    val detail: String? = null,
)

@Serializable
data class InformationRequestEvidenceEvaluationDto(
    val state: InformationRequestEvidenceRequirementState,
    val completesWork: Boolean,
    val satisfiedBySubstitute: Boolean,
    val findings: List<InformationRequestEvidenceFindingDto>,
)

@Serializable
data class InformationRequestEvidenceArtifactDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val artifactKey: String,
    val collectionState: InformationRequestEvidenceCollectionState,
    val artifactRevision: Long,
    val etag: String,
    val createdByCaller: Boolean,
    val stateReason: String? = null,
    @Serializable(with = TimestampSerializer::class) val stateChangedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val updatedAt: Timestamp,
    val versions: List<InformationRequestEvidenceVersionDto>,
)

@Serializable
data class InformationRequestEvidenceListDto(
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val evidenceETag: String,
    val artifacts: List<InformationRequestEvidenceArtifactDto>,
    val evaluation: InformationRequestEvidenceEvaluationDto? = null,
)

@Serializable
data class InformationRequestEvidenceCommandResultDto(
    val artifact: InformationRequestEvidenceArtifactDto,
    val evidenceETag: String,
    val artifactETag: String,
)
