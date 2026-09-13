package com.docuhyphen.app.api.resource.user

import com.docuhyphen.app.api.model.PlatformUserFeatureEntitlementDtoMapper
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementsUpdateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.subscription.PlatformUserFeatureEntitlementService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/platform/users/{appUserId}/feature-entitlements")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class PlatformUserFeatureEntitlementResource @Inject constructor(
    private val service: PlatformUserFeatureEntitlementService,
    private val mapper: PlatformUserFeatureEntitlementDtoMapper,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformUserFeatureEntitlementResource::class.java)
    }

    @GET
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
            handleException("Error fetching platform user feature entitlements", exception)
        }
    }

    @PUT
    fun replace(
        @PathParam("appUserId") appUserId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: PlatformUserFeatureEntitlementsUpdateRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                mapper.toDto(
                    service.replace(
                        appUserId = appUserId,
                        request = payload,
                        adminApprovalContext = AdminApprovalContext(requestId),
                    ),
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            if (exception is WebApplicationException) throw exception
            handleException("Error replacing platform user feature entitlements", exception)
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
