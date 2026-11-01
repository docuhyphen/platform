package com.docuhyphen.app.api.resource.audit.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/organizations/{organizationId}/audit-events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AuditOrganizationEventsResourceOperations
{
    @GET
    fun listOrganizationEvents(
        @PathParam("organizationId") organizationId: String,
        @QueryParam("categories") categories: String?,
        @QueryParam("cursorOccurredAt") cursorOccurredAt: String?,
        @QueryParam("cursorEventId") cursorEventId: String?,
        @QueryParam("occurredAfter") occurredAfter: String?,
        @QueryParam("occurredBefore") occurredBefore: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
    ): Response

    @GET
    @Path("/{eventId}")
    fun getOrganizationEvent(
        @PathParam("organizationId") organizationId: String,
        @PathParam("eventId") eventId: String,
    ): Response
}
