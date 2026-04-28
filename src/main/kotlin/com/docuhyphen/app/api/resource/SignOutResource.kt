package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.SignOutService
import com.docuhyphen.app.api.service.auth.TokenIssuanceService
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
    private val tokenIssuanceService: TokenIssuanceService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignOutResource::class.java)
    }

    @POST
    fun signOut(
        @QueryParam("outOfAllDevices") outOfAllDevices: Boolean = false
    ): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.delayEndpoint(3000, 6000)
            signOutService.signOut(outOfAllDevices)
            val clearCookie = tokenIssuanceService.buildClearRefreshTokenCookie()
            Response.ok().cookie(clearCookie).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error during sign-out.", exception)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred.")).build()
        }
    }
}