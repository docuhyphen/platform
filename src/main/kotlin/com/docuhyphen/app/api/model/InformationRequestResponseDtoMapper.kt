package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestResponseDto
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponseDraftResult

object InformationRequestResponseDtoMapper
{
    fun toDtos(result: InformationRequestResponseDraftResult): List<InformationRequestResponseDto> =
        result.responses.map { response ->
            toDto(
                response,
                result.requirementsById.getValue(response.informationRequestRequirementId),
                result.fieldValueProjectionsByRequirementId[response.informationRequestRequirementId],
            )
        }

    fun toDto(
        response: InformationRequestResponse,
        requirement: InformationRequestRequirement,
        fieldProjection: SchemaAssignmentDto? = null,
    ): InformationRequestResponseDto =
        InformationRequestResponseDto(
            informationRequestRequirementId = response.informationRequestRequirementId,
            sourceTemplateRequirementId = requirement.sourceTemplateRequirementId,
            sourceTemplateBindingId = requirement.sourceTemplateBindingId,
            occurrencePath = response.occurrencePath,
            disposition = response.disposition,
            narrative = response.narrative,
            fieldValueSetId = response.fieldValueSetId,
            fieldValueSetETag = fieldProjection?.etag,
            fieldValues = fieldProjection?.fields.orEmpty(),
            responseRevision = response.responseRevision,
            updatedAt = response.updatedAt,
            reconfirmationRequired = response.reconfirmationRequiredByAmendmentId != null,
        )
}
