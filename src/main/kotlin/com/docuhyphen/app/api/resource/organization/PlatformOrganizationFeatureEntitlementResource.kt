package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.model.dto.PlatformOrganizationFeatureEntitlementsUpdateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.platform.PlatformOrganizationService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/platform/organizations/{organizationId}/feature-entitlements")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class PlatformOrganizationFeatureEntitlementResource @Inject constructor(
    private val platformOrganizationService: PlatformOrganizationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformOrganizationFeatureEntitlementResource::class.java)
    }

    @GET
    fun get(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(
                platformOrganizationService.getFeatureEntitlements(organizationId, requestId),
            ).build()
        }
        catch (exception: Exception)
        {
            handleException("Error fetching platform organization feature entitlements", exception)
        }
    }

    @PUT
    fun replace(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: PlatformOrganizationFeatureEntitlementsUpdateRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                platformOrganizationService.replaceFeatureEntitlements(
                    organizationId = organizationId,
                    request = payload,
                    adminApprovalContext = AdminApprovalContext(requestId),
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            handleException("Error replacing platform organization feature entitlements", exception)
        }
    }

    private fun handleException(message: String, exception: Exception): Response
    {
        logger.error(message, exception)
        return when (exception)
        {
            is UnauthorizedException -> Response.status(Response.Status.FORBIDDEN)
                .entity(ResponseError(exception.message)).build()
            is OrganizationNotFoundException -> Response.status(Response.Status.NOT_FOUND)
                .entity(ResponseError(exception.message)).build()
            is IllegalArgumentException -> Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError(exception.message)).build()
            else -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("An unexpected error occurred")).build()
        }
    }
}
