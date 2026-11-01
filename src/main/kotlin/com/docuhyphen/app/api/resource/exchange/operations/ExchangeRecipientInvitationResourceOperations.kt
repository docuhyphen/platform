package com.docuhyphen.app.api.resource.exchange.operations

import com.docuhyphen.app.api.resource.model.ExchangeRecipientInvitationDecisionRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/exchange-recipient-invitations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ExchangeRecipientInvitationResourceOperations {
    @GET
    fun listPending(): Response

    @POST
    @Path("/{recipientId}/decisions")
    fun decide(
        @PathParam("recipientId") recipientId: String,
        request: ExchangeRecipientInvitationDecisionRequest,
    ): Response
}
