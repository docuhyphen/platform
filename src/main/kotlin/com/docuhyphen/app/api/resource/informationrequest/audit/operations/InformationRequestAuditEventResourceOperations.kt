package com.docuhyphen.app.api.resource.informationrequest.audit.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/audit-events")
@Produces(APPLICATION_JSON)
interface InformationRequestAuditEventResourceOperations
{
    @GET
    @Suppress("LongParameterList")
    fun list(
        @PathParam("id") id: String,
        @QueryParam("eventClass") eventClass: String?,
        @QueryParam("eventType") eventType: String?,
        @QueryParam("actorId") actorId: String?,
        @QueryParam("occurredAfter") occurredAfter: String?,
        @QueryParam("occurredBefore") occurredBefore: String?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
    ): Response
}
