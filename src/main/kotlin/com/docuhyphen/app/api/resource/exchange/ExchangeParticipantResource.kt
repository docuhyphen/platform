package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.AddExchangeParticipantRequest
import com.docuhyphen.app.api.service.exchange.ExchangeParticipantService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/exchanges/{exchangeId}/participants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ExchangeParticipantResource @Inject constructor(
    private val exchangeParticipantService: ExchangeParticipantService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeParticipantResource::class.java)
    }

    @POST
    fun addParticipant(
        @PathParam("exchangeId") exchangeId: String,
        request: AddExchangeParticipantRequest
    ): Response
    {
        return try
        {
            exchangeParticipantService.addExchangeParticipant(exchangeId, request.id)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException ->
                {
                    logger.error("Error adding exchange participant", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error adding exchange participant", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error initiating exchange", exception)

                    val responseError = ResponseError("An error occurred while initiating exchange")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @DELETE
    @Path("/{participantId}")
    fun removeParticipant(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("participantId") participantId: String
    ): Response
    {
        return try
        {
            exchangeParticipantService.removeExchangeParticipant(exchangeId, participantId)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException ->
                {
                    logger.error("Error initiating exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error initiating exchange", exception)

                    val responseError = ResponseError("An error occurred while initiating exchange")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }
}
