package com.docuhyphen.app.api.resource.informationrequest.disposal.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/disposal-standing")
@Produces(APPLICATION_JSON)
interface InformationRequestDisposalStandingResourceOperations
{
    @GET
    fun get(@PathParam("id") id: String): Response
}
