package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.resource.auth.operations.RealtimeTicketResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.RealtimeTicketService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class RealtimeTicketResource @Inject constructor(
    private val realtimeTicketService: RealtimeTicketService,
) : RealtimeTicketResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RealtimeTicketResource::class.java)
    }

    override fun issueTicket(): Response
    {
        return try
        {
            Response.ok(realtimeTicketService.issueForCurrentSession()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error issuing realtime ticket", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to issue realtime ticket"))
                        .build()
                }
            }
        }
    }
}
