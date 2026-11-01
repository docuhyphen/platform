package com.docuhyphen.app.api.resource.workflow.operations

import com.docuhyphen.app.api.resource.model.WorkflowDecisionRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/workflows/steps")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface WorkflowDecisionResourceOperations {
    @POST
    @Path("/{stepInstanceId}/decision")
    fun decide(
        @PathParam("stepInstanceId") stepInstanceId: String,
        payload: WorkflowDecisionRequest,
    ): Response

    @GET
    @Path("/pending")
    fun listPending(): Response
}
