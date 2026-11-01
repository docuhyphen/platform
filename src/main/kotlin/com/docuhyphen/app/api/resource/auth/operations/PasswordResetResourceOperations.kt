package com.docuhyphen.app.api.resource.auth.operations

import com.docuhyphen.app.api.resource.model.PasswordResetCompletionRequest
import com.docuhyphen.app.api.resource.model.PasswordResetInitiationRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/password-reset")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface PasswordResetResourceOperations
{
    @POST
    @Path("/initiation")
    fun initiatePasswordReset(payload: PasswordResetInitiationRequest): Response

    @POST
    @Path("/completion")
    fun completePasswordReset(payload: PasswordResetCompletionRequest): Response
}
