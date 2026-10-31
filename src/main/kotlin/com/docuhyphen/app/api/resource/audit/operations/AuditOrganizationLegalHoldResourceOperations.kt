package com.docuhyphen.app.api.resource.audit.operations

import com.docuhyphen.app.api.model.dto.AuditLegalHoldCreateRequestDto
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/organizations/{organizationId}/audit-legal-holds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AuditOrganizationLegalHoldResourceOperations {
    @POST
    fun placeOrganizationLegalHold(
        @PathParam("organizationId") organizationId: String,
        body: AuditLegalHoldCreateRequestDto,
    ): Response

    @GET
    fun listOrganizationLegalHolds(@PathParam("organizationId") organizationId: String): Response

    @POST
    @Path("/{holdId}/release")
    fun releaseOrganizationLegalHold(
        @PathParam("organizationId") organizationId: String,
        @PathParam("holdId") holdId: String,
    ): Response
}
