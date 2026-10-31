package com.docuhyphen.app.api.resource.informationrequest.party.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/trusted-recipient-candidates")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestTrustedRecipientCandidateResourceOperations {
    @GET
    fun list(@PathParam("id") id: String): Response
}
