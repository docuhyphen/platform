package com.docuhyphen.app.api.resource.informationrequest.audit.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-request-audit-events")
@Produces(APPLICATION_JSON)
interface InformationRequestAuditSearchResourceOperations
{
    @GET
    @Suppress("LongParameterList")
    fun search(
        @QueryParam("requestId") requestId: String?,
        @QueryParam("eventClass") eventClass: String?,
        @QueryParam("eventType") eventType: String?,
        @QueryParam("actorId") actorId: String?,
        @QueryParam("occurredAfter") occurredAfter: String?,
        @QueryParam("occurredBefore") occurredBefore: String?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
    ): Response
}
