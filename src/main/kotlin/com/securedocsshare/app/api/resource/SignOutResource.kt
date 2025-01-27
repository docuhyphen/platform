package com.securedocsshare.app.api.resource

import com.securedocsshare.app.api.resource.model.ResponseError
import com.securedocsshare.app.api.service.SignOutService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import org.slf4j.LoggerFactory

@Path("/auth/sign-out")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SignOutResource @Inject constructor(
    private val signOutService: SignOutService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignOutResource::class.java)
    }

    @POST
    fun signOut(): Response
    {
        return try
        {
            signOutService.signOut()
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error during sign-out.", exception)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred.")).build()
        }
    }
}