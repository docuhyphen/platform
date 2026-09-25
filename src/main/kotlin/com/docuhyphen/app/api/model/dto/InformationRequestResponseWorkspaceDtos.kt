package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class InformationRequestResponseWorkspaceDto(
    val request: InformationRequestDto,
    val templateVersion: InformationRequestTemplateVersionDto,
    val responseETag: String,
    val occurrences: List<InformationRequestGroupOccurrenceDto>,
    val schemaAssignment: SchemaAssignmentDto? = null,
    val responses: List<InformationRequestResponseDto> = emptyList(),
    val supportingEvidenceLinks: List<InformationRequestSupportingEvidenceLinkDto> = emptyList(),
    val evidenceUploadAvailable: Boolean = false,
    val evidenceMalwareScanning: Boolean = false,
)

@Serializable
data class InformationRequestSupportingEvidenceLinkDto(
    @Serializable(with = UUIDSerializer::class) val supportedRequirementId: UUID,
    @Serializable(with = UUIDSerializer::class) val supportingRequirementId: UUID,
)
