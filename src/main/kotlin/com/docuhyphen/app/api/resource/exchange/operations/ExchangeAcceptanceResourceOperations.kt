package com.docuhyphen.app.api.resource.exchange.operations

import com.docuhyphen.app.api.resource.model.ExchangeAcceptanceDecisionRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("exchanges/{exchangeId}/acceptance-decisions")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface ExchangeAcceptanceResourceOperations {
    @POST
    fun decide(
        @PathParam("exchangeId") exchangeId: String,
        request: ExchangeAcceptanceDecisionRequest,
    ): Response
}
