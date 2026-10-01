package com.docuhyphen.app.api.resource.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRefusalDto
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateValidationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST

object InformationRequestTemplateRefusalResponse
{
    const val TEMPLATE_INVALID = "INFORMATION_REQUEST_TEMPLATE_INVALID"

    fun of(exception: InformationRequestTemplateValidationException): Response =
        Response.status(BAD_REQUEST)
            .entity(
                InformationRequestTemplateRefusalDto(
                    errorMessage = exception.message,
                    reasonCode = TEMPLATE_INVALID,
                    sectionKey = exception.sectionKey,
                    requirementKey = exception.requirementKey,
                    groupKey = exception.groupKey,
                    reviewStageKey = exception.reviewStageKey,
                ),
            )
            .build()
}
