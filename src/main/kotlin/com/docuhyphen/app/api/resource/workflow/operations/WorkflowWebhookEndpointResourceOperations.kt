package com.docuhyphen.app.api.resource.workflow.operations

import com.docuhyphen.app.api.resource.model.RegisterWebhookRequestBody
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/organizations/{orgId}/workflow-webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface WorkflowWebhookEndpointResourceOperations {
    @GET
    fun listWebhooks(@PathParam("orgId") orgId: String): Response

    @POST
    fun registerWebhook(
        @PathParam("orgId") orgId: String,
        body: RegisterWebhookRequestBody
    ): Response

    @GET
    @Path("/{id}")
    fun getWebhook(
        @PathParam("orgId") orgId: String,
        @PathParam("id") id: String
    ): Response

    @POST
    @Path("/{id}/enable")
    fun enableWebhook(
        @PathParam("orgId") orgId: String,
        @PathParam("id") id: String
    ): Response

    @POST
    @Path("/{id}/disable")
    fun disableWebhook(
        @PathParam("orgId") orgId: String,
        @PathParam("id") id: String
    ): Response

    @POST
    @Path("/{id}/signing-secret/rotate")
    fun rotateSigningSecret(
        @PathParam("orgId") orgId: String,
        @PathParam("id") id: String
    ): Response
}
