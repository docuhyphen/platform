package com.docuhyphen.app.api.resource.application.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/application")
@Produces(MediaType.APPLICATION_JSON)
interface ApplicationScopedResourceOperations
{
    @GET
    @Path("/integration/ping")
    fun integrationPing(): Response

    @GET
    @Path("/service/ping")
    fun servicePing(): Response
}
