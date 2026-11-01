package com.docuhyphen.app.api.resource.subscription

import com.docuhyphen.app.api.model.SubscriptionTrialRequestDtoMapper
import com.docuhyphen.app.api.model.dto.SubscriptionTrialRequestCreateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.subscription.operations.SubscriptionTrialRequestResourceOperations
import com.docuhyphen.app.api.service.subscription.SelfServiceSubscriptionTrialRequestService
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialRequestConflictException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class SubscriptionTrialRequestResource @Inject constructor(
    private val service: SelfServiceSubscriptionTrialRequestService,
    private val mapper: SubscriptionTrialRequestDtoMapper,
) : SubscriptionTrialRequestResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SubscriptionTrialRequestResource::class.java)
    }

    override fun create(request: SubscriptionTrialRequestCreateRequest): Response
    {
        return try
        {
            Response.status(CREATED).entity(mapper.toDto(service.create(request))).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is SubscriptionTrialRequestConflictException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error creating subscription trial request", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun current(): Response
    {
        return try
        {
            Response.ok(mapper.toDto(service.current())).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is SubscriptionTrialRequestConflictException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error loading current subscription trial request", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}
