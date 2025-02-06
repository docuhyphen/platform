package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.InvalidEmailException
import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.exception.UserNotFoundException
import com.dochyphen.app.api.model.BasicModelConverter.Companion.toDto
import com.dochyphen.app.api.model.dto.SharingSessionBasicDto
import com.dochyphen.app.api.model.entity.DetailedModelConverter
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.resource.model.SharingSessionInitiationRequest
import com.dochyphen.app.api.resource.model.UpdateSharingSessionRequest
import com.dochyphen.app.api.service.*
import com.dochyphen.app.api.service.sharingsession.SharingSessionDocumentService
import com.dochyphen.app.api.service.sharingsession.SharingSessionInitiationService
import com.dochyphen.app.api.service.sharingsession.SharingSessionParticipantService
import com.dochyphen.app.api.service.sharingsession.SharingSessionRetrievalService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/sharing-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SharingSessionResource @Inject constructor(
    private val sharingSessionDocumentService: SharingSessionDocumentService,
    private val sharingSessionInitiationService: SharingSessionInitiationService,
    private val sharingSessionRetrievalService: SharingSessionRetrievalService,
    private val sharingSessionUpdateService: SharingSessionUpdateService,
    private val sharingSessionParticipantService: SharingSessionParticipantService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionResource::class.java)
    }

    @POST
    fun initiateSharingSession(sharingSessionInitiationRequest: SharingSessionInitiationRequest): Response
    {
        return try
        {
            val sharingSession = with(sharingSessionInitiationRequest) {
                sharingSessionInitiationService.initiateSharingSession(
                    initialShareMessage,
                    description,
                    receiverEmail,
                    sessionName,
                    sessionDocuments,
                    requestReceiverSignIn,
                    allowDocumentAddition,
                    allowDocumentDeletion,
                    allowDocumentDownload,
                    allowDocumentUpdate,
                    allowDocumentUpload,
                    participants
                )
            }

            Response.ok(toDto(sharingSession)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException,
                is InvalidEmailException,
                is UserNotFoundException ->
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

    @GET
    fun getSharingSessions(): Response
    {

        return try
        {
            val sessions = sharingSessionRetrievalService.getAllSessionsForSignedInAppUser()
            var sessionDTOs = arrayOf<SharingSessionBasicDto?>()

            if (sessions.isNotEmpty())
            {
                sessionDTOs = sessions.map { toDto(it) }.toTypedArray()
            }

            Response.ok(sessionDTOs).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException ->
                {
                    logger.error("Error getting app user sharing sessions", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error getting app user sharing sessions", exception)

                    val responseError = ResponseError("An error occurred while getting app user sharing sessions")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @GET
    @Path("/{sessionId}")
    fun getSharingSession(@PathParam("sessionId") sessionId: String): Response
    {
        return try
        {
            val sharingSession = sharingSessionRetrievalService.getSharingSession(sessionId)

            Response.ok(DetailedModelConverter.toDo(sharingSession)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException ->
                {
                    logger.error("Error getting sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

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

    @PUT
    @Path("/{sessionId}")
    fun updateSharingSession(
        @PathParam("sessionId") sessionId: String,
        request: UpdateSharingSessionRequest
    ): Response
    {
        return try
        {
            sharingSessionUpdateService.updateSharingSession(sessionId, request)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException ->
                {
                    logger.error("Error adding sharing session document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error updating sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error updating sharing session", exception)

                    val responseError = ResponseError("An error occurred while updating sharing session")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }
}