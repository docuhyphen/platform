package com.docuhyphen.app.api.resource.informationrequest.notice.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/notices")
@Produces(APPLICATION_JSON)
interface InformationRequestNoticeResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response
}
