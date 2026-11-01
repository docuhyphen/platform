package com.docuhyphen.app.api.resource.user

import com.docuhyphen.app.api.model.PlatformUserSubscriptionDtoMapper
import com.docuhyphen.app.api.resource.model.PlatformUserSubscriptionPolicyListResponse
import com.docuhyphen.app.api.resource.model.PlatformUserSubscriptionPolicyRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.user.operations.PlatformUserSubscriptionPolicyResourceOperations
import com.docuhyphen.app.api.service.subscription.PlatformUserSubscriptionPolicyService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class PlatformUserSubscriptionPolicyResource @Inject constructor(
    private val service: PlatformUserSubscriptionPolicyService,
    private val mapper: PlatformUserSubscriptionDtoMapper,
) : PlatformUserSubscriptionPolicyResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformUserSubscriptionPolicyResource::class.java)
        private const val DEFAULT_LIMIT = 50
    }

    override fun list(
        query: String?,
        limit: Int?,
        offset: Int?,
        requestId: String?,
    ): Response
    {
        return try
        {
            val result = service.list(query, limit ?: DEFAULT_LIMIT, offset ?: 0, requestId)
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
                    logger.error("Error listing platform user subscription policies", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
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
                    logger.error("Error fetching platform user subscription policy", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun update(
        appUserId: String,
        requestId: String?,
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
                    logger.error("Error updating platform user subscription policy", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}
