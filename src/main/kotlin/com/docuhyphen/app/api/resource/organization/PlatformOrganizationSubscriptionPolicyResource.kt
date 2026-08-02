package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.resource.model.PlatformOrganizationSubscriptionPolicyRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.PlatformOrganizationDtoMapper
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.PlatformOrganizationSubscriptionPolicyService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import org.slf4j.LoggerFactory

@Path("/platform/organizations/{organizationId}/subscription-policy")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class PlatformOrganizationSubscriptionPolicyResource @Inject constructor(
    private val platformOrganizationSubscriptionPolicyService: PlatformOrganizationSubscriptionPolicyService,
    private val platformOrganizationDtoMapper: PlatformOrganizationDtoMapper,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformOrganizationSubscriptionPolicyResource::class.java)
    }

    @GET
    fun getEffectivePolicy(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val policy = platformOrganizationSubscriptionPolicyService.getEffectivePolicy(organizationId, requestId)
            Response.ok(platformOrganizationDtoMapper.toSubscriptionPolicy(policy)).build()
        }
        catch (exception: Exception)
        {
            handleException("Error fetching platform organization subscription policy", exception)
        }
    }

    @DELETE
    fun deletePolicy(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val policy = platformOrganizationSubscriptionPolicyService.deletePolicy(
                organizationId = organizationId,
                adminApprovalContext = AdminApprovalContext(requestId = requestId),
            )
            Response.ok(platformOrganizationDtoMapper.toSubscriptionPolicy(policy)).build()
        }
        catch (exception: Exception)
        {
            handleException("Error deleting platform organization subscription policy", exception)
        }
    }

    @PUT
    fun upsertPolicy(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: PlatformOrganizationSubscriptionPolicyRequest,
    ): Response
    {
        return try
        {
            val policy = platformOrganizationSubscriptionPolicyService.upsertPolicy(
                organizationId = organizationId,
                request = payload,
                adminApprovalContext = AdminApprovalContext(requestId = requestId),
            )
            Response.ok(platformOrganizationDtoMapper.toSubscriptionPolicy(policy)).build()
        }
        catch (exception: Exception)
        {
            handleException("Error upserting platform organization subscription policy", exception)
        }
    }

    private fun handleException(message: String, exception: Exception): Response
    {
        logger.error(message, exception)

        return when (exception)
        {
            is UnauthorizedException -> Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
            is IllegalArgumentException -> Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
            else -> Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred")).build()
        }
    }
}



