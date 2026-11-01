package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organizations/{organizationId}/identity-providers/secrets/rotation")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface OrganizationIdentityProviderRotationRunbookResourceOperations {
    @GET
    @Path("/status")
    fun getRotationStatus(@PathParam("organizationId") organizationId: String): Response

    @GET
    @Path("/preview")
    fun previewEmergencyRotation(@PathParam("organizationId") organizationId: String): Response

    @POST
    @Path("/run")
    fun runEmergencyRotation(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response
}
