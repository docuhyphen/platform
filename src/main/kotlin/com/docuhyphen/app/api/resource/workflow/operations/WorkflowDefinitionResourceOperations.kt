package com.docuhyphen.app.api.resource.workflow.operations

import com.docuhyphen.app.api.model.workflow.CloneWorkflowRequest
import com.docuhyphen.app.api.model.workflow.CreateWorkflowDefinitionRequest
import com.docuhyphen.app.api.model.workflow.PatchWorkflowPublishedRequest
import com.docuhyphen.app.api.model.workflow.PatchWorkflowStatusRequest
import com.docuhyphen.app.api.model.workflow.UpdateWorkflowDefinitionRequest
import com.docuhyphen.app.api.resource.RequestHeaders
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/workflows")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface WorkflowDefinitionResourceOperations {
    @GET
    @Path("/definitions")
    fun listDefinitions(
        @QueryParam("scope") scope: String?,
        @QueryParam("tag") tag: String?,
        @QueryParam("triggerEvent") triggerEvent: String?,
        @QueryParam("isTemplate") isTemplate: Boolean?,
    ): Response

    @POST
    @Path("/definitions")
    fun createDefinition(request: CreateWorkflowDefinitionRequest): Response

    @GET
    @Path("/definitions/{id}")
    fun getDefinition(@PathParam("id") id: String): Response

    @PUT
    @Path("/definitions/{id}")
    fun updateDefinition(
        @PathParam("id") id: String,
        request: UpdateWorkflowDefinitionRequest,
    ): Response

    @PATCH
    @Path("/definitions/{id}/status")
    fun patchStatus(
        @PathParam("id") id: String,
        request: PatchWorkflowStatusRequest,
    ): Response

    @PATCH
    @Path("/definitions/{id}/published")
    fun patchPublished(
        @PathParam("id") id: String,
        request: PatchWorkflowPublishedRequest,
    ): Response

    @DELETE
    @Path("/definitions/{id}")
    fun deleteDefinition(
        @PathParam("id") id: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @POST
    @Path("/definitions/{id}/clone")
    fun cloneDefinition(
        @PathParam("id") id: String,
        request: CloneWorkflowRequest,
    ): Response

    @GET
    @Path("/triggers")
    fun listTriggers(): Response

    @GET
    @Path("/entity-lookup")
    fun entityLookup(
        @QueryParam("lookupType") lookupType: String?,
        @QueryParam("q") q: String?,
    ): Response

    @GET
    @Path("/instances")
    fun listInstances(
        @QueryParam("status") status: String?,
        @QueryParam("subjectResourceType") subjectResourceType: String?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("pageSize") @DefaultValue("20") pageSize: Int,
    ): Response

    @GET
    @Path("/instances/{id}")
    fun getInstanceDetail(@PathParam("id") id: String): Response
}
