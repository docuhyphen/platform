package com.docuhyphen.app.api.resource.audit.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/admin/applications/{applicationId}/audit-events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AuditApplicationEventsResourceOperations
{
    @GET
    fun listApplicationEvents(
        @PathParam("applicationId") applicationId: String,
        @QueryParam("cursorOccurredAt") cursorOccurredAt: String?,
        @QueryParam("cursorEventId") cursorEventId: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
    ): Response
}
