package com.docuhyphen.app.api.resource.informationrequest.creation.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/document-placeholders")
@Produces(APPLICATION_JSON)
interface InformationRequestDocumentPlaceholderResourceOperations {
    @GET
    fun list(@PathParam("id") id: String): Response
}
