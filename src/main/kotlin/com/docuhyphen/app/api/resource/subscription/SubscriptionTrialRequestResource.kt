package com.docuhyphen.app.api.resource.subscription

import com.docuhyphen.app.api.model.SubscriptionTrialRequestDtoMapper
import com.docuhyphen.app.api.model.dto.SubscriptionTrialRequestCreateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.SelfServiceSubscriptionTrialRequestService
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialRequestConflictException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/subscription-trial-requests")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class SubscriptionTrialRequestResource @Inject constructor(
    private val service: SelfServiceSubscriptionTrialRequestService,
    private val mapper: SubscriptionTrialRequestDtoMapper,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SubscriptionTrialRequestResource::class.java)
    }

    @POST
    fun create(request: SubscriptionTrialRequestCreateRequest): Response
    {
        return try
        {
            Response.status(Response.Status.CREATED).entity(mapper.toDto(service.create(request))).build()
        }
        catch (exception: Exception)
        {
            handleException("Error creating subscription trial request", exception)
        }
    }

    @GET
    @Path("/current")
    fun current(): Response
    {
        return try
        {
            Response.ok(mapper.toDto(service.current())).build()
        }
        catch (exception: Exception)
        {
            handleException("Error loading current subscription trial request", exception)
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
            is SubscriptionTrialRequestConflictException -> Response.status(Response.Status.CONFLICT)
                .entity(ResponseError(exception.message)).build()
            is IllegalArgumentException -> Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError(exception.message)).build()
            else -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("An unexpected error occurred")).build()
        }
    }
}
