package com.docuhyphen.app.api.resource.audit.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/exchanges/{exchangeId}/audit-events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AuditExchangeEventsResourceOperations
{
    @GET
    fun listExchangeEvents(
        @PathParam("exchangeId") exchangeId: String,
        @QueryParam("cursorOccurredAt") cursorOccurredAt: String?,
        @QueryParam("cursorEventId") cursorEventId: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
    ): Response
}
