package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import java.sql.Timestamp

object InformationRequestResponseWorkspaceDtoMapper
{
    fun toDto(
        request: InformationRequestDto,
        title: String,
        templateVersion: InformationRequestTemplateVersionDto,
        responseETag: String,
        occurrences: List<InformationRequestGroupOccurrenceDto>,
        schemaAssignment: SchemaAssignmentDto?,
        responses: List<InformationRequestResponseDto>,
        supportingEvidenceLinks: List<InformationRequestSupportingEvidenceLinkDto> = emptyList(),
        evidenceUploadAvailable: Boolean = false,
        evidenceMalwareScanning: Boolean = false,
        executionStanding: InformationRequestExecutionStanding,
    ): InformationRequestResponseWorkspaceDto =
        InformationRequestResponseWorkspaceDto(
            request = request,
            title = title,
            templateVersion = templateVersion,
            responseETag = responseETag,
            occurrences = occurrences,
            schemaAssignment = schemaAssignment,
            responses = responses,
            supportingEvidenceLinks = supportingEvidenceLinks,
            evidenceUploadAvailable = evidenceUploadAvailable,
            evidenceMalwareScanning = evidenceMalwareScanning,
            executionStanding = InformationRequestExecutionStandingDtoMapper.toDto(executionStanding),
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
