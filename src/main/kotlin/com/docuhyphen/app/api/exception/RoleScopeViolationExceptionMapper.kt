package com.docuhyphen.app.api.exception

import com.docuhyphen.app.api.resource.model.ResponseError
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import kotlinx.serialization.json.Json

@Provider
class RoleScopeViolationExceptionMapper : ExceptionMapper<RoleScopeViolationException>
{
    override fun toResponse(exception: RoleScopeViolationException): Response =
        Response
            .status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                Json.encodeToString(
                    ResponseError.serializer(),
                    ResponseError(
                        errorMessage = exception.message ?: "Invalid role for this scope",
                        reasonCode = "ROLE_SCOPE_VIOLATION",
                    ),
                )
            )
            .build()
}
