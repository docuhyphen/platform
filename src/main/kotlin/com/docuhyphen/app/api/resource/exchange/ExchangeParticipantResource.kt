package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.resource.exchange.operations.ExchangeParticipantResourceOperations
import com.docuhyphen.app.api.resource.model.AddExchangeParticipantRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.ExchangeParticipantService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class ExchangeParticipantResource @Inject constructor(
    private val exchangeParticipantService: ExchangeParticipantService
) : ExchangeParticipantResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeParticipantResource::class.java)
    }

    override fun addParticipant(
        exchangeId: String,
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
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error adding Exchange participant", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while adding Exchange participant"))
                        .build()
                }
            }
        }
    }

    override fun removeParticipant(
        exchangeId: String,
        participantId: String
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
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error removing Exchange participant", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while removing Exchange participant"))
                        .build()
                }
            }
        }
    }
}
