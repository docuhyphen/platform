package com.docuhyphen.app.api.resource.informationrequest.oversight.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/platform/information-request-health")
@Produces(APPLICATION_JSON)
interface PlatformInformationRequestHealthResourceOperations
{
    @GET
    fun get(@HeaderParam("X-Request-Id") requestId: String?): Response
}
