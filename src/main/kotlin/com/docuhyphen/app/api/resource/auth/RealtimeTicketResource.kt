package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.RealtimeTicketService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/auth/realtime-tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class RealtimeTicketResource @Inject constructor(
    private val realtimeTicketService: RealtimeTicketService,
    private val authTokenContext: AuthTokenContext,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RealtimeTicketResource::class.java)
    }

    @POST
    fun issueTicket(): Response
    {
        return try
        {
            val sessionId = authTokenContext.userSessionId
                ?: return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Session is unavailable"))
                    .build()
            val appUserId = authTokenContext.authToken.appUser?.id
                ?: return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("User is unavailable"))
                    .build()
            Response.ok(realtimeTicketService.issue(sessionId, appUserId)).build()
        }
        catch (e: Exception)
        {
            logger.error("Error issuing realtime ticket", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to issue realtime ticket"))
                .build()
        }
    }
}
