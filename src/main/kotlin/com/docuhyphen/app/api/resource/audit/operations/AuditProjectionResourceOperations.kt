package com.docuhyphen.app.api.resource.audit.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AuditProjectionResourceOperations
{
    @GET
    @Path("/users/me/security-events")
    fun listMySecurityEvents(
        @QueryParam("cursorOccurredAt") cursorOccurredAt: String?,
        @QueryParam("cursorEventId") cursorEventId: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
    ): Response

    @GET
    @Path("/platform/audit-events")
    fun listPlatformAuditEvents(
        @QueryParam("categories") categories: String?,
        @QueryParam("cursorOccurredAt") cursorOccurredAt: String?,
        @QueryParam("cursorEventId") cursorEventId: String?,
        @QueryParam("occurredAfter") occurredAfter: String?,
        @QueryParam("occurredBefore") occurredBefore: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
    ): Response
}
