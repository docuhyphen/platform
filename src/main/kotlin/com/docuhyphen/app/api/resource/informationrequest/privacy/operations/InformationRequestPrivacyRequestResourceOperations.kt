package com.docuhyphen.app.api.resource.informationrequest.privacy.operations

import com.docuhyphen.app.api.resource.model.RecordInformationRequestPrivacyRequestRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
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
