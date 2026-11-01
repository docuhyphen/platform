package com.docuhyphen.app.api.resource.auth.operations

import com.docuhyphen.app.api.resource.model.ApplicationTokenRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/application")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ApplicationAuthResourceOperations
{
    @POST
    @Path("/token")
    fun getApplicationToken(payload: ApplicationTokenRequest): Response
}
