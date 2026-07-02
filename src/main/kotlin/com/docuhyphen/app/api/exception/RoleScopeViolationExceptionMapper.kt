package com.docuhyphen.app.api.exception

import com.docuhyphen.app.api.resource.model.ResponseError
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import kotlinx.serialization.json.Json

/**
 * Maps [RoleScopeViolationException] to a 400 Bad Request response.
 *
 * Thrown when a role value is supplied for the wrong scope, e.g. APPLICATION used as an
 * OrganizationRoleName, or OWNER (Exchange Share) used as an organization membership role.
 * Returning 400 rather than letting the server produce a 500 ensures the client can
 * distinguish a malformed request from an internal fault.
 */
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
