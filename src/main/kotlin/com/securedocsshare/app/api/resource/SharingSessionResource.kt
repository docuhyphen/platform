package com.securedocsshare.app.api.resource

import com.securedocsshare.app.api.exception.InvalidEmailException
import com.securedocsshare.app.api.exception.UserNotFoundException
import com.securedocsshare.app.api.resource.model.*
import com.securedocsshare.app.api.service.SharingSessionService
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

@Path("/sharing-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SharingSessionResource @Inject constructor(
    private val sharingSessionService: SharingSessionService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionResource::class.java)
    }

    @POST
    @Path("/initiate")
    fun initiateSharingSession(initiateShareSessionRequest: InitiateShareSessionRequest): Response
    {
        return try
        {
            val sharingSession = with(initiateShareSessionRequest) {
                sharingSessionService.initiateSharingSession(
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
    @Path("/initiator/{initiatorId}")
    fun getSharingSessionsForInitiator(@PathParam("initiatorId") initiatorId: UUID): Response
    {
        return try
        {
            val sessions = sharingSessionService.getSharingSessionsForInitiator(initiatorId)
            Response.ok(sessions).build()
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
    @Path("/receiver/{receiverId}")
    fun getSharingSessionsForReceiver(@PathParam("receiverId") receiverId: UUID): Response
    {
        return try
        {
            val sessions = sharingSessionService.getSharingSessionsForReceiver(receiverId)
            Response.ok(sessions).build()
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
    @Path("/{sessionId}/status")
    fun updateSharingSessionStatus(
        @PathParam("sessionId") sessionId: String,
        request: UpdateSharingSessionStatus
    ): Response
    {
        return try
        {
            sharingSessionService.updateSharingSessionStatus(sessionId, request.status)
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
    @Path("/{sessionId}/participants")
    fun addParticipant(
        @PathParam("sessionId") sessionId: String,
        request: SharingSessionParticipant
    ): Response
    {
        return try
        {
            sharingSessionService.addSharingSessionParticipant(sessionId, request.id, request.role)
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
            sharingSessionService.removeSharingSessionParticipant(sessionId, participantId)
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
    @Path("/{sessionId}/documents")
    fun addSessionDocument(
        request: AddSharingSessionDocumentRequest,
        @PathParam("sessionId") sessionId: String
    ): Response
    {
        return try
        {
            val document = with(request) {
                sharingSessionService.addDocument(
                    sessionId,
                    documentType,
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

    @POST
    @Path("/{sessionId}/documents/{documentId}/delete")
    fun deleteDocument(
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            sharingSessionService.deleteDocument(sessionId, documentId)
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
                sharingSessionService.uploadDocument(file, sessionId, documentId, encryptionMode, performedBy)
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
                sharingSessionService.updateDocument(
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
    @Path("/{sessionId}/documents/{documentId}/download")
    fun downloadDocument(
        request: DownloadShareSessionDocumentRequest
    ): Response
    {
        return try
        {
            val file = with(request) {
                sharingSessionService.downloadDocument(sessionId, documentId)
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
}