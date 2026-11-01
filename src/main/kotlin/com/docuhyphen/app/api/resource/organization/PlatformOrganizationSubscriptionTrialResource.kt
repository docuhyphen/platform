package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.model.PlatformSubscriptionTrialDtoMapper
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialConversionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialEndRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialExtensionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialStartRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.organization.operations.PlatformOrganizationSubscriptionTrialResourceOperations
import com.docuhyphen.app.api.service.subscription.PlatformSubscriptionTrialService
import com.docuhyphen.app.api.service.subscription.PlatformSubscriptionTrialTransitionService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class PlatformOrganizationSubscriptionTrialResource @Inject constructor(
    private val service: PlatformSubscriptionTrialService,
    private val transitionService: PlatformSubscriptionTrialTransitionService,
    private val mapper: PlatformSubscriptionTrialDtoMapper,
) : PlatformOrganizationSubscriptionTrialResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformOrganizationSubscriptionTrialResource::class.java)
    }

    override fun start(
        organizationId: String,
        requestId: String?,
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
                    logger.error("Error starting platform organization subscription trial", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun extend(
        organizationId: String,
        requestId: String?,
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
                    logger.error("Error extending platform organization subscription trial", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun end(
        organizationId: String,
        requestId: String?,
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
                    logger.error("Error ending platform organization subscription trial", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun convert(
        organizationId: String,
        requestId: String?,
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
                    logger.error("Error converting platform organization subscription trial", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}
