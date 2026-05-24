package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.resource.model.ApplicationScopeProbeResponse
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.Instant

@Path("/auth/application")
@Produces(MediaType.APPLICATION_JSON)
class ApplicationScopedResource
{
    @GET
    @Path("/integration/ping")
    fun integrationPing(): Response
    {
        return Response.ok(
            ApplicationScopeProbeResponse(
                endpointGroup = "integration",
                authorized = true,
                issuedAtEpochMillis = Instant.now().toEpochMilli(),
            )
        ).build()
    }

    @GET
    @Path("/service/ping")
    fun servicePing(): Response
    {
        return Response.ok(
            ApplicationScopeProbeResponse(
                endpointGroup = "service",
                authorized = true,
                issuedAtEpochMillis = Instant.now().toEpochMilli(),
            )
        ).build()
    }
}

