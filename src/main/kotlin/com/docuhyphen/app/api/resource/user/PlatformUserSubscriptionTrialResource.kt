package com.docuhyphen.app.api.resource.user

import com.docuhyphen.app.api.model.PlatformSubscriptionTrialDtoMapper
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialConversionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialEndRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialExtensionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialStartRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.user.operations.PlatformUserSubscriptionTrialResourceOperations
import com.docuhyphen.app.api.service.subscription.PlatformSubscriptionTrialService
import com.docuhyphen.app.api.service.subscription.PlatformSubscriptionTrialTransitionService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class PlatformUserSubscriptionTrialResource @Inject constructor(
    private val service: PlatformSubscriptionTrialService,
    private val transitionService: PlatformSubscriptionTrialTransitionService,
    private val mapper: PlatformSubscriptionTrialDtoMapper,
) : PlatformUserSubscriptionTrialResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformUserSubscriptionTrialResource::class.java)
    }

    override fun start(
        appUserId: String,
        requestId: String?,
        request: PlatformSubscriptionTrialStartRequest,
    ): Response
    {
        return try
        {
            val result = service.startUserTrial(appUserId, request, AdminApprovalContext(requestId = requestId))
            Response.status(CREATED).entity(mapper.toDto(result)).build()
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
                    logger.error("Error starting platform user subscription trial", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun extend(
        appUserId: String,
        requestId: String?,
        request: PlatformSubscriptionTrialExtensionRequest,
    ): Response
    {
        return try
        {
            val result = service.extendUserTrial(appUserId, request, AdminApprovalContext(requestId = requestId))
            Response.ok(mapper.toDto(result)).build()
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
                    logger.error("Error extending platform user subscription trial", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun end(
        appUserId: String,
        requestId: String?,
        request: PlatformSubscriptionTrialEndRequest,
    ): Response
    {
        return try
        {
            val result = transitionService.endUserTrial(
                appUserId,
                request,
                AdminApprovalContext(requestId = requestId),
            )
            Response.ok(mapper.toDto(result)).build()
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
                    logger.error("Error ending platform user subscription trial", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun convert(
        appUserId: String,
        requestId: String?,
        request: PlatformSubscriptionTrialConversionRequest,
    ): Response
    {
        return try
        {
            val result = transitionService.convertUserTrial(
                appUserId,
                request,
                AdminApprovalContext(requestId = requestId),
            )
            Response.status(CREATED).entity(mapper.toDto(result)).build()
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
                    logger.error("Error converting platform user subscription trial", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}
