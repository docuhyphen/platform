package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.resource.model.SharingSessionParticipantRequest
import com.dochyphen.app.api.service.sharingsession.SharingSessionParticipantService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/sharing-sessions/{sessionId}/participants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SharingSessionParticipantResource @Inject constructor(
    private val sharingSessionParticipantService: SharingSessionParticipantService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionParticipantResource::class.java)
    }

    @POST
    fun addParticipant(
        @PathParam("sessionId") sessionId: String,
        request: SharingSessionParticipantRequest
    ): Response
    {
        return try
        {
            sharingSessionParticipantService.addSharingSessionParticipant(sessionId, request.id, request.role)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException ->
                {
                    logger.error("Error adding sharing session participant", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error adding sharing session participant", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error initiating sharing session", exception)

                    val responseError = ResponseError("An error occurred while initiating sharing session")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @DELETE
    @Path("/{participantId}")
    fun removeParticipant(
        @PathParam("sessionId") sessionId: String,
        @PathParam("participantId") participantId: String
    ): Response
    {
        return try
        {
            sharingSessionParticipantService.removeSharingSessionParticipant(sessionId, participantId)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException ->
                {
                    logger.error("Error initiating sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error initiating sharing session", exception)

                    val responseError = ResponseError("An error occurred while initiating sharing session")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }
}