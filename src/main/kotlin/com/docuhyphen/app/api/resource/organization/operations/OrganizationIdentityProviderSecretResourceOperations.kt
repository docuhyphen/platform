package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretActivateRequest
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRetireRequest
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotateRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organizations/{organizationId}/identity-providers/{configId}/secrets")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface OrganizationIdentityProviderSecretResourceOperations {
    @POST
    @Path("/rotate")
    fun rotateSecret(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        payload: OrganizationIdpSecretRotateRequest,
    ): Response

    @POST
    @Path("/activate")
    fun activateVersion(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        payload: OrganizationIdpSecretActivateRequest,
    ): Response

    @POST
    @Path("/rollback")
    fun rollbackToPreviousVersion(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @GET
    @Path("/status")
    fun getSecretStatus(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
    ): Response

    @POST
    @Path("/disable")
    fun disableSecret(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @POST
    @Path("/enable")
    fun enableSecret(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @DELETE
    fun retireSecret(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        payload: OrganizationIdpSecretRetireRequest,
    ): Response
}
