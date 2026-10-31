package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.resource.exchange.operations.ExchangeAcceptanceResourceOperations
import com.docuhyphen.app.api.resource.model.ExchangeAcceptanceDecisionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.ExchangeAcceptanceService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class ExchangeAcceptanceResource @Inject constructor(
    private val exchangeAcceptanceService: ExchangeAcceptanceService,
) : ExchangeAcceptanceResourceOperations
{
    companion object {
        private val logger = LoggerFactory.getLogger(ExchangeAcceptanceResource::class.java)
    }

    override fun decide(
        exchangeId: String,
        request: ExchangeAcceptanceDecisionRequest,
    ): Response {
        return try
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

                is ExchangeStatusTransitionException ->
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

                else -> {
                    logger.error("Error deciding Exchange acceptance", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while deciding Exchange acceptance"))
                        .build()
                }
            }
        }
    }
}
