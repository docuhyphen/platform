package com.docuhyphen.app.api.resource.subscription

import com.docuhyphen.app.api.model.SubscriptionTrialRequestDtoMapper
import com.docuhyphen.app.api.model.dto.SubscriptionTrialRequestDecisionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.PlatformSubscriptionTrialRequestService
import io.quarkus.security.ForbiddenException
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

@Path("/platform/subscription-trial-requests")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class PlatformSubscriptionTrialRequestResource @Inject constructor(
    private val service: PlatformSubscriptionTrialRequestService,
    private val mapper: SubscriptionTrialRequestDtoMapper,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformSubscriptionTrialRequestResource::class.java)
    }

    @GET
    fun list(
        @QueryParam("status") status: String?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
    ): Response
    {
        return try
        {
            Response.ok(mapper.toDto(service.list(status, limit ?: 25, offset ?: 0))).build()
        }
        catch (exception: Exception)
        {
            handleException("Error listing platform subscription trial requests", exception)
        }
    }

    @PATCH
    @Path("/{requestId}/status")
    fun decide(
        @PathParam("requestId") requestId: String,
        @HeaderParam("X-Request-Id") approvalRequestId: String?,
        request: SubscriptionTrialRequestDecisionRequest,
    ): Response
    {
        return try
        {
            val result = service.decide(
                requestId,
                request,
                AdminApprovalContext(requestId = approvalRequestId),
            )
            Response.ok(mapper.toDto(result)).build()
        }
        catch (exception: Exception)
        {
            handleException("Error deciding platform subscription trial request", exception)
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
