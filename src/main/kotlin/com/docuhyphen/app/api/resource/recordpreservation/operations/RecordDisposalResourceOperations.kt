package com.docuhyphen.app.api.resource.recordpreservation.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/record-disposals")
@Produces(APPLICATION_JSON)
interface RecordDisposalResourceOperations {
    @GET
    fun list(): Response
}
