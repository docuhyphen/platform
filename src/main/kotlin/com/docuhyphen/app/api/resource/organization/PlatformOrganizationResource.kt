package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.model.dto.PlatformOrganizationStatusUpdateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.platform.PlatformOrganizationService
import com.docuhyphen.app.api.service.platform.PlatformOrganizationStatusService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/platform/organizations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class PlatformOrganizationResource @Inject constructor(
    private val platformOrganizationService: PlatformOrganizationService,
    private val platformOrganizationStatusService: PlatformOrganizationStatusService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformOrganizationResource::class.java)
    }

    @GET
    fun list(
        @QueryParam("query") query: String?,
        @QueryParam("status") status: String?,
        @QueryParam("tierCode") tierCode: String?,
        @QueryParam("sort") sort: String?,
        @QueryParam("direction") direction: String?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(
                platformOrganizationService.list(
                    query = query,
                    status = status,
                    tierCode = tierCode,
                    sort = sort,
                    direction = direction,
                    limit = limit ?: 50,
                    offset = offset ?: 0,
                    requestId = requestId,
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            handleException("Error listing restricted platform organization summaries", exception)
        }
    }

    @GET
    @Path("/{organizationId}")
    fun get(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(platformOrganizationService.get(organizationId, requestId)).build()
        }
        catch (exception: Exception)
        {
            handleException("Error fetching restricted platform organization summary", exception)
        }
    }

    @PATCH
    @Path("/{organizationId}/status")
    fun updateStatus(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: PlatformOrganizationStatusUpdateRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                platformOrganizationStatusService.update(
                    organizationId = organizationId,
                    request = payload,
                    adminApprovalContext = AdminApprovalContext(requestId),
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            handleException("Error updating platform organization status", exception)
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
