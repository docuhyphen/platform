package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.SessionActivityService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/auth/session-activities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SessionActivityResource @Inject constructor(
    private val sessionActivityService: SessionActivityService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SessionActivityResource::class.java)
    }

    @POST
    fun recordActivity(): Response
    {
        return try
        {
            Response.ok(sessionActivityService.recordActivity()).build()
        }
        catch (e: UnauthorizedException)
        {
            logger.warn("Rejected session activity update", e)
            Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Session is no longer active"))
                .build()
        }
        catch (e: Exception)
        {
            logger.error("Error recording session activity", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to record session activity"))
                .build()
        }
    }
}
