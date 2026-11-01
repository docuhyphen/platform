package com.docuhyphen.app.api.resource.user

import com.docuhyphen.app.api.model.PlatformUserFeatureEntitlementDtoMapper
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementsUpdateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.user.operations.PlatformUserFeatureEntitlementResourceOperations
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.subscription.PlatformUserFeatureEntitlementService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class PlatformUserFeatureEntitlementResource @Inject constructor(
    private val service: PlatformUserFeatureEntitlementService,
    private val mapper: PlatformUserFeatureEntitlementDtoMapper,
) : PlatformUserFeatureEntitlementResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformUserFeatureEntitlementResource::class.java)
    }

    override fun get(
        appUserId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(mapper.toDto(service.get(appUserId, requestId))).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching platform user feature entitlements", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun replace(
        appUserId: String,
        requestId: String?,
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
            when (exception)
            {
                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error replacing platform user feature entitlements", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}
