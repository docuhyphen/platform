package com.docuhyphen.app.api.resource.auth.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface UserSessionResourceOperations
{
    @GET
    fun listSessions(): Response

    @DELETE
    @Path("/{sessionId}")
    fun revokeSession(@PathParam("sessionId") sessionId: String): Response

    @DELETE
    @Path("/{sessionId}/record")
    fun deleteSessionRecord(@PathParam("sessionId") sessionId: String): Response
}
