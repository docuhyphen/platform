package com.docuhyphen.app.api.resource.informationrequest.capability.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-request-capabilities")
@Produces(APPLICATION_JSON)
interface InformationRequestCapabilityResourceOperations
{
    @GET
    fun get(): Response
}
