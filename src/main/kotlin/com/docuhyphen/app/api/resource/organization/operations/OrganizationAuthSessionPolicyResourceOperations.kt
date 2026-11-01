package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyUpdateRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organizations/{organizationId}/auth/session-policy")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface OrganizationAuthSessionPolicyResourceOperations {
    @GET
    @Path("/effective")
    fun getEffectivePolicy(
        @PathParam("organizationId") organizationId: String,
        @QueryParam("appUserId") appUserId: String?,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @GET
    fun getSettings(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @PUT
    @Path("/{configId}")
    fun updateIdpSessionPolicy(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        payload: OrganizationAuthSessionPolicyUpdateRequest,
    ): Response
}
