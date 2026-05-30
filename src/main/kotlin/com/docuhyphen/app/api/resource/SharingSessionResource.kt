package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.NoAuthOtpException
import com.docuhyphen.app.api.exception.InvalidEmailException
import com.docuhyphen.app.api.exception.SharingSessionNotFoundException
import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer.Companion.toDto
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.DocumentDetailedDto
import com.docuhyphen.app.api.model.dto.SharingSessionBasicDto
import com.docuhyphen.app.api.model.dto.SharingSessionDetailedDto
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SharingSessionInitiationDto
import com.docuhyphen.app.api.resource.model.UpdateSharingSessionRequest
import com.docuhyphen.app.api.service.sharingsession.*
import com.docuhyphen.app.api.service.storage.FileStorageService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS
import org.slf4j.LoggerFactory

@Path("/sharing-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SharingSessionResource @Inject constructor(
    private val sharingSessionDocumentService: SharingSessionDocumentService,
    private val sharingSessionInitiationService: SharingSessionInitiationService,
    private val sharingSessionRetrievalService: SharingSessionRetrievalService,
    private val sharingSessionUpdateService: SharingSessionUpdateService,
    private val sharingSessionParticipantService: SharingSessionParticipantService,
    private val authTokenContext: AuthTokenContext,
    private val fileStorageService: FileStorageService,

    )
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionResource::class.java)
    }

    @POST
    fun initiateSharingSession(sharingSessionInitiationDto: SharingSessionInitiationDto): Response
    {
        return try
        {
            val sharingSession = sharingSessionInitiationService.initiateSharingSession(sharingSessionInitiationDto)

            Response.ok(toDto(sharingSession)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException,
                is InvalidEmailException,
                is AppUserNotFoundException ->
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

    @HEAD
    fun checkUserHasSharingSessions(): Response
    {
        return try
        {
            val hasSessions = sharingSessionRetrievalService.checkUserHasSharingSessions()
            if (hasSessions)
            {
                Response.ok().build()
            }
            else
            {
                Response.status(Response.Status.NO_CONTENT).build()
            }
        }
        catch (exception: Exception)
        {
            logger.error("Error checking if user has sharing sessions", exception)
            val responseError = ResponseError("An error occurred while checking if user has sharing sessions")
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
        }
    }

    @GET
    fun getSharingSessions(): Response
    {
//        ResourceEndpointDelayHelper.delayEndpoint(1000, 3000)

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
//        ResourceEndpointDelayHelper.delayEndpoint(2000, 4000)

        return try
        {
            val sharingSession = sharingSessionRetrievalService.getSharingSession(sessionId)
            val sharingSessionDto = DetailedEntityToDtoTransformer.toDto(sharingSession)

            Response.ok(enrichSessionWithFileSizes(sharingSessionDto)).build()
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
                    logger.error("Error getting sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error getting sharing session", exception)

                    val responseError = ResponseError("An error occurred while getting sharing session")
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

    @POST
    @Path("/{sessionId}/recipient-otp")
    fun issueSessionRecipientOtp(@PathParam("sessionId") sessionId: String): Response
    {
        return try
        {
            sharingSessionUpdateService.issueRecipientOtp(sessionId)
            Response.status(Response.Status.NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is NoAuthOtpException ->
                {
                    val response = Response
                        .status(
                            if (exception.reasonCode == "OTP_RATE_LIMITED" || exception.reasonCode == "OTP_LOCKED")
                                TOO_MANY_REQUESTS
                            else
                                Response.Status.BAD_REQUEST,
                        )
                        .entity(
                            ResponseError(
                                errorMessage = exception.message,
                                reasonCode = exception.reasonCode,
                                retryAfterSeconds = exception.retryAfterSeconds,
                            )
                        )

                    if (exception.retryAfterSeconds != null)
                    {
                        response.header("Retry-After", exception.retryAfterSeconds)
                    }

                    response.build()
                }

                is SharingSessionNotFoundException ->
                {
                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is IllegalArgumentException ->
                {
                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is ForbiddenException ->
                {
                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(ResponseError("Disable \"Require recipient sign in\" and save changes before sending an access code."))
                        .build()
                }

                else ->
                {
                    logger.error("Error issuing recipient OTP for sharing session", exception)
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while sending access code"))
                        .build()
                }
            }
        }
    }

    @DELETE
    @Path("/{sessionId}")
    fun deleteSharingSession(@PathParam("sessionId") sessionId: String): Response
    {
        return try
        {
            sharingSessionUpdateService.deleteSharingSession(sessionId)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException ->
                {
                    logger.error("Error deleting sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error deleting sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error deleting sharing session", exception)

                    val responseError = ResponseError("An error occurred while deleting sharing session")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @GET
    @Path("/search")
    fun searchSharingSessions(
        @QueryParam("query") query: String?,
        @QueryParam("status") status: String?,
        @QueryParam("initiatedBy") initiatedBy: Boolean?,
        @QueryParam("page") page: Int = 0,
        @QueryParam("size") size: Int = 20,
        @QueryParam("sortBy") sortBy: String = "createdDate",
        @QueryParam("sortDirection") sortDirection: String = "DESC"
    ): Response
    {
        return try
        {

            val result = sharingSessionRetrievalService.searchSharingSessions(
                query, status, initiatedBy, page, size, sortBy, sortDirection
            )

            Response.ok(result).build()

        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException ->
                {
                    logger.error("Error searching sharing sessions", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error searching sharing sessions", exception)
                    val responseError = ResponseError("An error occurred while searching sharing sessions")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    private fun enrichSessionWithFileSizes(sessionDto: SharingSessionDetailedDto?): SharingSessionDetailedDto?
    {
        if (sessionDto == null) return null

        return sessionDto.copy(
            documents = sessionDto.documents.map { document -> enrichDocumentWithFileSize(document) }
        )
    }

    private fun enrichDocumentWithFileSize(document: DocumentDetailedDto?): DocumentDetailedDto?
    {
        if (document == null || document.uploadDate == null) return document

        val documentId = document.id ?: return document
        val documentType = document.type
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?.let { runCatching { DocumentType.valueOf(it) }.getOrNull() }
            ?: return document

        val storageKey = "$documentId${DocumentType.toFileExtension(documentType)}"
        val fileSize = runCatching { fileStorageService.getDocumentSizeBytes(storageKey) }.getOrNull()

        return document.copy(fileSize = fileSize)
    }
}