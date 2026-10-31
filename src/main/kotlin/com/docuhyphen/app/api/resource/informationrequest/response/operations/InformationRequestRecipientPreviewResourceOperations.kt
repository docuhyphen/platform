package com.docuhyphen.app.api.resource.informationrequest.response.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/parties/{partyId}/recipient-preview")
@Produces(APPLICATION_JSON)
interface InformationRequestRecipientPreviewResourceOperations {
    @GET
    fun preview(@PathParam("id") id: String, @PathParam("partyId") partyId: String): Response
}
