package com.docuhyphen.app.api.resource.informationrequest.template

import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateRefusalDtoMapper
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateValidationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST

object InformationRequestTemplateRefusalResponse
{
    fun of(exception: InformationRequestTemplateValidationException): Response =
        Response.status(BAD_REQUEST)
            .entity(InformationRequestTemplateRefusalDtoMapper.toDto(exception))
            .build()
}
