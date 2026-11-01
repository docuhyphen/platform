package com.docuhyphen.app.api.exception

import com.docuhyphen.app.api.model.SubscriptionDtoMapper
import com.docuhyphen.app.api.model.dto.SubscriptionDenialDto
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import kotlinx.serialization.json.Json

@Provider
class SubscriptionDenialExceptionMapper : ExceptionMapper<SubscriptionDenialException>
{
    override fun toResponse(exception: SubscriptionDenialException): Response =
        Response
            .status(Response.Status.FORBIDDEN)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                Json.encodeToString(
                    SubscriptionDenialDto.serializer(),
                    SubscriptionDtoMapper.toDto(exception.denial),
                )
            )
            .build()
}

