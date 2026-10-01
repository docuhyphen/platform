package com.docuhyphen.app.api.resource.informationrequest.privacy.operations

import com.docuhyphen.app.api.resource.model.RecordInformationRequestPrivacyRequestRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-request-privacy-requests")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestPrivacyRequestResourceOperations
{
    @POST
    fun record(request: RecordInformationRequestPrivacyRequestRequest?): Response

    @GET
    fun list(@QueryParam("subjectIdentityRefId") subjectIdentityRefId: String?): Response

    @GET
    @Path("/{privacyRequestId}")
    fun get(@PathParam("privacyRequestId") privacyRequestId: String): Response
}
