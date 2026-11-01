package com.docuhyphen.app.api.resource.security.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/security-incidents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface SecurityIncidentResourceOperations
{
    @GET
    fun list(
        @QueryParam("limit") @DefaultValue("25") limit: Int,
        @QueryParam("incidentType") incidentType: String?,
    ): Response
}
