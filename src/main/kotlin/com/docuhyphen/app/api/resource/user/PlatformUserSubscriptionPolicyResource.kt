package com.docuhyphen.app.api.resource.user

import com.docuhyphen.app.api.model.PlatformUserSubscriptionDtoMapper
import com.docuhyphen.app.api.resource.model.PlatformUserSubscriptionPolicyListResponse
import com.docuhyphen.app.api.resource.model.PlatformUserSubscriptionPolicyRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.PlatformUserSubscriptionPolicyService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/platform/users")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class PlatformUserSubscriptionPolicyResource @Inject constructor(
    private val service: PlatformUserSubscriptionPolicyService,
    private val mapper: PlatformUserSubscriptionDtoMapper,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformUserSubscriptionPolicyResource::class.java)
    }

    @GET
    @Path("/subscription-policies")
    fun list(
        @QueryParam("query") query: String?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val result = service.list(query, limit ?: 50, offset ?: 0, requestId)
            Response.ok(
                PlatformUserSubscriptionPolicyListResponse(
                    total = result.total,
                    limit = result.limit,
                    offset = result.offset,
                    items = result.items.map(mapper::toDto),
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            if (exception is WebApplicationException) throw exception
            handleException("Error listing platform user subscription policies", exception)
        }
    }

    @GET
    @Path("/{appUserId}/subscription-policy")
    fun get(
        @PathParam("appUserId") appUserId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(mapper.toDto(service.get(appUserId, requestId))).build()
        }
        catch (exception: Exception)
        {
            if (exception is WebApplicationException) throw exception
            handleException("Error fetching platform user subscription policy", exception)
        }
    }

    @PATCH
    @Path("/{appUserId}/subscription-policy")
    fun update(
        @PathParam("appUserId") appUserId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
        request: PlatformUserSubscriptionPolicyRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                mapper.toDto(
                    service.update(
                        appUserId,
                        request,
                        AdminApprovalContext(requestId = requestId),
                    ),
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            if (exception is WebApplicationException) throw exception
            handleException("Error updating platform user subscription policy", exception)
        }
    }

    private fun handleException(message: String, exception: Exception): Response
    {
        logger.error(message, exception)
        return when (exception)
        {
            is UnauthorizedException -> Response.status(Response.Status.FORBIDDEN)
                .entity(ResponseError(exception.message)).build()
            is IllegalArgumentException -> Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError(exception.message)).build()
            else -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("An unexpected error occurred")).build()
        }
    }
}
