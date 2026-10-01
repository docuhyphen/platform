package com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/carry-forwards")
@Produces(APPLICATION_JSON)
interface InformationRequestCarryForwardResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response
}
