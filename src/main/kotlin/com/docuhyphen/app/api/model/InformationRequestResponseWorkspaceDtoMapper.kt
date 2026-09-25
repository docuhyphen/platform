package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestDto
import com.docuhyphen.app.api.model.dto.InformationRequestGroupOccurrenceDto
import com.docuhyphen.app.api.model.dto.InformationRequestResponseDto
import com.docuhyphen.app.api.model.dto.InformationRequestResponseWorkspaceDto
import com.docuhyphen.app.api.model.dto.InformationRequestSupportingEvidenceLinkDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestSupportingEvidenceLink
import java.sql.Timestamp

object InformationRequestResponseWorkspaceDtoMapper
{
    fun toDto(
        request: InformationRequestDto,
        templateVersion: InformationRequestTemplateVersionDto,
        responseETag: String,
        occurrences: List<InformationRequestGroupOccurrenceDto>,
        schemaAssignment: SchemaAssignmentDto?,
        responses: List<InformationRequestResponseDto>,
        supportingEvidenceLinks: List<InformationRequestSupportingEvidenceLinkDto> = emptyList(),
        evidenceUploadAvailable: Boolean = false,
        evidenceMalwareScanning: Boolean = false,
    ): InformationRequestResponseWorkspaceDto =
        InformationRequestResponseWorkspaceDto(
            request = request,
            templateVersion = templateVersion,
            responseETag = responseETag,
            occurrences = occurrences,
            schemaAssignment = schemaAssignment,
            responses = responses,
            supportingEvidenceLinks = supportingEvidenceLinks,
            evidenceUploadAvailable = evidenceUploadAvailable,
            evidenceMalwareScanning = evidenceMalwareScanning,
        )

    fun linkDto(link: InformationRequestSupportingEvidenceLink): InformationRequestSupportingEvidenceLinkDto =
        InformationRequestSupportingEvidenceLinkDto(link.supportedRequirementId, link.supportingRequirementId)

    fun responseDto(
        request: InformationRequest,
        requirement: InformationRequestRequirement,
        response: InformationRequestResponse?,
        fieldProjection: SchemaAssignmentDto?,
        updatedAt: Timestamp,
    ): InformationRequestResponseDto =
        response?.let { InformationRequestResponseDtoMapper.toDto(it, requirement, fieldProjection) }
            ?: InformationRequestResponseDto(
                informationRequestRequirementId = requirement.id,
                sourceTemplateRequirementId = requirement.sourceTemplateRequirementId,
                sourceTemplateBindingId = requirement.sourceTemplateBindingId,
                occurrencePath = requirement.occurrencePath,
                disposition = InformationRequestResponseDisposition.NOT_ANSWERED,
                fieldValueSetETag = fieldProjection?.etag,
                fieldValues = fieldProjection?.fields.orEmpty(),
                responseRevision = request.responseRevision,
                updatedAt = updatedAt,
            )
}
