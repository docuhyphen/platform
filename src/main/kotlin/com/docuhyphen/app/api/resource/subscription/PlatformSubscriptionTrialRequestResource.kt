package com.docuhyphen.app.api.resource.subscription

import com.docuhyphen.app.api.model.SubscriptionTrialRequestDtoMapper
import com.docuhyphen.app.api.model.dto.SubscriptionTrialRequestDecisionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.subscription.operations.PlatformSubscriptionTrialRequestResourceOperations
import com.docuhyphen.app.api.service.subscription.PlatformSubscriptionTrialRequestService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class PlatformSubscriptionTrialRequestResource @Inject constructor(
    private val service: PlatformSubscriptionTrialRequestService,
    private val mapper: SubscriptionTrialRequestDtoMapper,
) : PlatformSubscriptionTrialRequestResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformSubscriptionTrialRequestResource::class.java)
        private const val DEFAULT_LIMIT = 25
    }

    override fun list(
        status: String?,
        limit: Int?,
        offset: Int?,
    ): Response
    {
        return try
        {
            Response.ok(mapper.toDto(service.list(status, limit ?: DEFAULT_LIMIT, offset ?: 0))).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing platform subscription trial requests", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun decide(
        requestId: String,
        approvalRequestId: String?,
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
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error deciding platform subscription trial request", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}
