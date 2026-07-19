package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.exception.ExchangeRecipientEligibilityException
import com.docuhyphen.app.api.exception.OrganizationTrustException
import com.docuhyphen.app.api.exception.WorkflowConflictException
import com.docuhyphen.app.api.resource.model.ExchangeAcceptanceDecisionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.ExchangeAcceptanceService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("exchanges/{exchangeId}/acceptance-decisions")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class ExchangeAcceptanceResource @Inject constructor(
    private val exchangeAcceptanceService: ExchangeAcceptanceService,
)
{
    @POST
    fun decide(
        @PathParam("exchangeId") exchangeId: String,
        request: ExchangeAcceptanceDecisionRequest,
    ): Response =
        try
        {
            exchangeAcceptanceService.decide(exchangeId, request.decision, request.reason)
            Response.noContent().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()
                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()
                is WorkflowConflictException ->
                    Response.status(Response.Status.CONFLICT).entity(ResponseError(exception.message)).build()
                is OrganizationTrustException ->
                    Response.status(Response.Status.CONFLICT)
                        .entity(ResponseError("This Exchange can no longer be accepted"))
                        .build()
                is ExchangeRecipientEligibilityException ->
                    Response.status(Response.Status.CONFLICT)
                        .entity(ResponseError("This Exchange can no longer be accepted"))
                        .build()
                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()
                else ->
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while deciding Exchange acceptance"))
                        .build()
            }
        }
}
