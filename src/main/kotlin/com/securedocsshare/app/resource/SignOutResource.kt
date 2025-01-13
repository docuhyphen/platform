package com.securedocsshare.app.resource

import com.securedocsshare.app.api.model.SignOutRequest
import com.securedocsshare.app.service.AuthenticationService
import com.securedocsshare.app.service.SignOutService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/sign-out")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SignOutResource @Inject constructor(
    private val authService: AuthenticationService,
    private val signOutService: SignOutService,
)
{
    @POST
    @Path("/sign-out")
    fun signOut(payload: SignOutRequest): Response
    {
        signOutService.signOut(payload.appUser)

        return Response.ok(mapOf("message" to "")).build()
    }
}
