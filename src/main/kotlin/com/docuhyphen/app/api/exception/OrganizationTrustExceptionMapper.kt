package com.docuhyphen.app.api.exception

import com.docuhyphen.app.api.resource.model.ResponseError
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class OrganizationTrustExceptionMapper : ExceptionMapper<OrganizationTrustException>
{
    override fun toResponse(exception: OrganizationTrustException): Response
    {
        val status = when (exception)
        {
            is ExternalIdentityResolutionUnavailableException -> Response.Status.NOT_FOUND
            is OrganizationTrustNotFoundException -> Response.Status.NOT_FOUND
            is OrganizationTrustAuthorizationException -> Response.Status.FORBIDDEN
            is OrganizationTrustValidationException -> Response.Status.BAD_REQUEST
            is OrganizationTrustRateLimitException -> Response.Status.TOO_MANY_REQUESTS
            is OrganizationTrustConflictException -> Response.Status.CONFLICT
            else -> Response.Status.INTERNAL_SERVER_ERROR
        }
        val message = if (status == Response.Status.INTERNAL_SERVER_ERROR)
            "Trusted Organization request failed"
        else
            exception.message
        return Response.status(status).entity(ResponseError(message)).build()
    }
}
