package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.InvalidEmailException
import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.exception.UserNotFoundException
import com.dochyphen.app.api.model.BasicModelConverter.Companion.toDto
import com.dochyphen.app.api.model.dto.SharingSessionBasicDto
import com.dochyphen.app.api.model.entity.DetailedModelConverter
import com.dochyphen.app.api.resource.model.*
import com.dochyphen.app.api.service.*
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

            Response.ok(sharingSession).build()
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
    fun getAllSharingSessions(): Response
    {

        return try
        {
            val sessions = sharingSessionRetrievalService.getAllSessionsForSignInAppUser()
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
                is IllegalArgumentException,
                is SharingSessionNotFoundException ->
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

    @POST
    @Path("/{sessionId}/documents")
    fun addSessionDocument(
        request: AddSharingSessionDocumentRequest,
        @PathParam("sessionId") sessionId: String
    ): Response
    {
        return try
        {
            val document = with(request) {
                sharingSessionDocumentService.addDocument(
                    sessionId,
                    title,
                    documentType,
                    restrictedType
                )
            }

            Response.ok(toDto(document)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException ->
                {
                    logger.error("Error adding sharing session document", exception)

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
    @Path("/{sessionId}/documents/{documentId}")
    fun deleteDocument(
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            sharingSessionDocumentService.deleteDocument(sessionId, documentId)
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

    @POST
    @Path("/{sessionId}/upload")
    fun uploadDocument(
        uploadShareSessionDocumentRequest: UploadShareSessionDocumentRequest,
        @PathParam("sessionId") sessionId: String
    ): Response
    {
        return try
        {
            val encryptionKey = with(uploadShareSessionDocumentRequest) {
                sharingSessionDocumentService.uploadDocument(file, sessionId, documentId, performedBy)
            }
            Response.ok(mapOf("encryptionKey" to encryptionKey)).build()
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

    @PUT
    @Path("/{sessionId}/documents/{documentId}")
    fun updateDocument(
        request: UpdateShareSessionDocumentRequest,
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            val document = with(request) {
                sharingSessionDocumentService.updateDocument(
                    sessionId,
                    documentId,
                    title,
                    type,
                    restrictedType
                )
            }

            Response.ok(document).build()
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

    @GET
    @Path("/{sessionId}/documents/{documentId}/file")
    fun downloadDocument(
        request: DownloadShareSessionDocumentRequest
    ): Response
    {
        return try
        {
            val file = with(request) {
                sharingSessionDocumentService.downloadDocument(sessionId, documentId)
            }

            Response.ok(file).build()
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


    @POST
    @Path("/{sessionId}/participants")
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

    @DELETE
    @Path("/{sessionId}/participants/{participantId}")
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