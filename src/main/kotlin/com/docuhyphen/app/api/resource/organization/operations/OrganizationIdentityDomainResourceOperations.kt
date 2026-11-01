package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.model.OrganizationIdentityDomainCreateRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organizations/{organizationId}/identity-domains")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface OrganizationIdentityDomainResourceOperations {
    @GET
    fun list(@PathParam("organizationId") organizationId: String): Response

    @POST
    fun create(
        @PathParam("organizationId") organizationId: String,
        request: OrganizationIdentityDomainCreateRequest,
    ): Response

    @POST
    @Path("/{domainId}/verification")
    fun verify(
        @PathParam("organizationId") organizationId: String,
        @PathParam("domainId") domainId: String,
    ): Response

    @DELETE
    @Path("/{domainId}")
    fun delete(
        @PathParam("organizationId") organizationId: String,
        @PathParam("domainId") domainId: String,
    ): Response
}
