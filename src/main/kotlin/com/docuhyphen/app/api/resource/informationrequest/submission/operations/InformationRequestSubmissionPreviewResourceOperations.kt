package com.docuhyphen.app.api.resource.informationrequest.submission.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/submission-preview")
@Produces(APPLICATION_JSON)
interface InformationRequestSubmissionPreviewResourceOperations
{
    @GET
    fun preview(@PathParam("id") id: String, @QueryParam("stageKey") stageKey: String?): Response
}
