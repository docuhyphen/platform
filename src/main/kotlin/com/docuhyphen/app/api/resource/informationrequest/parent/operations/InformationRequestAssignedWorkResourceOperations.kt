package com.docuhyphen.app.api.resource.informationrequest.parent.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-request-assignments")
@Produces(APPLICATION_JSON)
interface InformationRequestAssignedWorkResourceOperations {
    @GET
    fun list(): Response
}
