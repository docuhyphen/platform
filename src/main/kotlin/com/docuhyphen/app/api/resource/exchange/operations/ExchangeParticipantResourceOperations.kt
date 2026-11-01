package com.docuhyphen.app.api.resource.exchange.operations

import com.docuhyphen.app.api.resource.model.AddExchangeParticipantRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/exchanges/{exchangeId}/participants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ExchangeParticipantResourceOperations {
    @POST
    fun addParticipant(
        @PathParam("exchangeId") exchangeId: String,
        request: AddExchangeParticipantRequest
    ): Response

    @DELETE
    @Path("/{participantId}")
    fun removeParticipant(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("participantId") participantId: String
    ): Response
}
