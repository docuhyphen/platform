package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.OrganizationIdpConfigRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organizations/{organizationId}/identity-providers")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface OrganizationIdentityProviderConfigResourceOperations {
    @GET
    fun list(@PathParam("organizationId") organizationId: String): Response

    @POST
    fun create(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        payload: OrganizationIdpConfigRequest,
    ): Response

    @PUT
    @Path("/{configId}")
    fun update(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        payload: OrganizationIdpConfigRequest,
    ): Response

    @DELETE
    @Path("/{configId}")
    fun delete(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response
}
