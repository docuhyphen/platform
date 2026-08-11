package com.docuhyphen.app.api.exception

import com.docuhyphen.app.api.model.SubscriptionDtoMapper
import com.docuhyphen.app.api.model.dto.SubscriptionDenialDto
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import kotlinx.serialization.json.Json

/**
 * Maps [SubscriptionDenialException] to a 403 Forbidden response carrying the structured
 * refusal.
 *
 * 403 is used for commercial refusals so they stay distinguishable from a missing
 * authentication (401) and from security rate limiting (429). The body explains the allowance
 * and the plan that lifts it so the app never has to present a plan limit as a permission
 * error.
 */
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



