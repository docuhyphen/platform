package com.docuhyphen.app.api.resource.informationrequest.submission.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.WILDCARD
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/submissions/{packageId}/evidence")
interface InformationRequestSubmissionEvidenceResourceOperations {
    @GET
    @Path("/{versionId}/content")
    @Produces(WILDCARD)
    fun download(
        @PathParam("id") id: String,
        @PathParam("packageId") packageId: String,
        @PathParam("versionId") versionId: String,
    ): Response

    @GET
    @Path("/{versionId}/preview")
    @Produces(WILDCARD)
    fun preview(
        @PathParam("id") id: String,
        @PathParam("packageId") packageId: String,
        @PathParam("versionId") versionId: String,
    ): Response
}
