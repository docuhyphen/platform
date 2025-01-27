package com.securedocsshare.app.api.resource

import com.securedocsshare.app.api.service.UserSessionService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

@Path("/user-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class UserSessionResource @Inject constructor(
    private val userSessionService: UserSessionService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(UserSessionResource::class.java)
    }

    @GET
    @Path("/{userId}")
    fun getAllSessionsForUser(@PathParam("userId") userId: UUID): Response
    {
        return try
        {
            val sessions = userSessionService.getAllSessionsForUser(userId)
            Response.ok(sessions).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error retrieving sessions for user", exception)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(mapOf("error" to exception.message)).build()
        }
    }
}