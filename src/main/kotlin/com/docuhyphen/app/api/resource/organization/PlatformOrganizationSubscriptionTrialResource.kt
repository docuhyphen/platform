package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.model.PlatformSubscriptionTrialDtoMapper
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialExtensionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialEndRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialConversionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialStartRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.subscription.PlatformSubscriptionTrialService
import com.docuhyphen.app.api.service.subscription.PlatformSubscriptionTrialTransitionService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/platform/organizations/{organizationId}/subscription-trials")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class PlatformOrganizationSubscriptionTrialResource @Inject constructor(
    private val service: PlatformSubscriptionTrialService,
    private val transitionService: PlatformSubscriptionTrialTransitionService,
    private val mapper: PlatformSubscriptionTrialDtoMapper,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformOrganizationSubscriptionTrialResource::class.java)
    }

    @POST
    fun start(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
        request: PlatformSubscriptionTrialStartRequest,
    ): Response
    {
        return try
        {
            val result = service.startOrganizationTrial(
                organizationId,
                request,
                AdminApprovalContext(requestId = requestId),
            )
            Response.status(Response.Status.CREATED).entity(mapper.toDto(result)).build()
        }
        catch (exception: Exception)
        {
            if (exception is WebApplicationException) throw exception
            handleException("Error starting platform organization subscription trial", exception)
        }
    }

    @PATCH
    @Path("/current")
    fun extend(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
        request: PlatformSubscriptionTrialExtensionRequest,
    ): Response
    {
        return try
        {
            val result = service.extendOrganizationTrial(
                organizationId,
                request,
                AdminApprovalContext(requestId = requestId),
            )
            Response.ok(mapper.toDto(result)).build()
        }
        catch (exception: Exception)
        {
            if (exception is WebApplicationException) throw exception
            handleException("Error extending platform organization subscription trial", exception)
        }
    }

    @DELETE
    @Path("/current")
    fun end(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
        request: PlatformSubscriptionTrialEndRequest,
    ): Response
    {
        return try
        {
            val result = transitionService.endOrganizationTrial(
                organizationId,
                request,
                AdminApprovalContext(requestId = requestId),
            )
            Response.ok(mapper.toDto(result)).build()
        }
        catch (exception: Exception)
        {
            if (exception is WebApplicationException) throw exception
            handleException("Error ending platform organization subscription trial", exception)
        }
    }

    @POST
    @Path("/current/conversions")
    fun convert(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
        request: PlatformSubscriptionTrialConversionRequest,
    ): Response
    {
        return try
        {
            val result = transitionService.convertOrganizationTrial(
                organizationId,
                request,
                AdminApprovalContext(requestId = requestId),
            )
            Response.status(Response.Status.CREATED).entity(mapper.toDto(result)).build()
        }
        catch (exception: Exception)
        {
            if (exception is WebApplicationException) throw exception
            handleException("Error converting platform organization subscription trial", exception)
        }
    }

    private fun handleException(message: String, exception: Exception): Response
    {
        logger.error(message, exception)
        return when (exception)
        {
            is UnauthorizedException -> Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError(exception.message)).build()
            is ForbiddenException -> Response.status(Response.Status.FORBIDDEN)
                .entity(ResponseError(exception.message)).build()
            is IllegalArgumentException -> Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError(exception.message)).build()
            else -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("An unexpected error occurred")).build()
        }
    }
}
