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
import com.docuhyphen.app.api.resource.model.GrantSessionShareRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SharingSessionInitiationDto
import com.docuhyphen.app.api.resource.model.UpdateSessionShareRoleRequest
import com.docuhyphen.app.api.resource.model.UpdateSharingSessionRequest
import com.docuhyphen.app.api.service.sharingsession.*
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.storage.FileStorageService
import com.docuhyphen.app.api.service.auth.authz.ShareConstraints
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
    private val shareQueryService: ShareQueryService,
    private val shareService: ShareService,
    private val sessionAccessManagementService: SessionAccessManagementService,
    private val authTokenContext: AuthTokenContext,
    private val fileStorageService: FileStorageService,
    private val appUserService: AppUserService,

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
            val enriched = enrichSessionWithRecipient(enrichSessionWithPermissions(enrichSessionWithFileSizes(sharingSessionDto)))

            // TEMP DEBUG — remove once permissions issue resolved
            val rawConstraints = shareService.recipientConstraintsJson(java.util.UUID.fromString(sessionId))
            logger.info(
                "DEBUG getSharingSession sessionId={} rawConstraints={} dto.allowDocumentUpload={} dto.allowDocumentUpdate={} dto.allowDocumentDeletion={} dto.allowDocumentDownload={} dto.allowDocumentAddition={}",
                sessionId,
                rawConstraints,
                enriched?.allowDocumentUpload,
                enriched?.allowDocumentUpdate,
                enriched?.allowDocumentDeletion,
                enriched?.allowDocumentDownload,
                enriched?.allowDocumentAddition,
            )

            Response.ok(enriched).build()
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

    @GET
    @Path("/{sessionId}/access")
    fun getSharingSessionAccess(@PathParam("sessionId") sessionId: String): Response
    {
        return try
        {
            val view = shareQueryService.getSessionAccessView(java.util.UUID.fromString(sessionId))
            Response.ok(view.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException ->
                {
                    val responseError = ResponseError("Invalid session id")
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error getting sharing session access view", exception)
                    val responseError = ResponseError("An error occurred while getting sharing session access")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @POST
    @Path("/{sessionId}/access")
    fun grantSharingSessionAccess(
        @PathParam("sessionId") sessionId: String,
        request: GrantSessionShareRequest,
    ): Response
    {
        return try
        {
            val sessionUuid = java.util.UUID.fromString(sessionId)
            sessionAccessManagementService.grantAccess(
                sessionId = sessionUuid,
                principalKind = request.principalKind,
                principalId = request.principalId,
                roleName = request.roleName,
                constraintsJson = request.constraintsJson,
                expiresAtEpochMillis = request.expiresAtEpochMillis,
            )
            Response.ok(shareQueryService.getSessionAccessView(sessionUuid).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            mapAccessMutationError(exception, "granting sharing session access")
        }
    }

    @PATCH
    @Path("/{sessionId}/access/{shareId}")
    fun updateSharingSessionAccessRole(
        @PathParam("sessionId") sessionId: String,
        @PathParam("shareId") shareId: String,
        request: UpdateSessionShareRoleRequest,
    ): Response
    {
        return try
        {
            val sessionUuid = java.util.UUID.fromString(sessionId)
            sessionAccessManagementService.changeRole(
                sessionId = sessionUuid,
                shareId = java.util.UUID.fromString(shareId),
                roleName = request.roleName,
                constraintsJson = request.constraintsJson,
            )
            Response.ok(shareQueryService.getSessionAccessView(sessionUuid).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            mapAccessMutationError(exception, "updating sharing session access role")
        }
    }

    @DELETE
    @Path("/{sessionId}/access/{shareId}")
    fun revokeSharingSessionAccess(
        @PathParam("sessionId") sessionId: String,
        @PathParam("shareId") shareId: String,
    ): Response
    {
        return try
        {
            val sessionUuid = java.util.UUID.fromString(sessionId)
            sessionAccessManagementService.revokeAccess(
                sessionId = sessionUuid,
                shareId = java.util.UUID.fromString(shareId),
            )
            Response.ok(shareQueryService.getSessionAccessView(sessionUuid).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            mapAccessMutationError(exception, "revoking sharing session access")
        }
    }

    private fun mapAccessMutationError(exception: Exception, context: String): Response =
        when (exception)
        {
            is ForbiddenException ->
                Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

            is SharingSessionNotFoundException ->
                Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

            is IllegalArgumentException ->
                Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

            else ->
            {
                logger.error("Error $context", exception)
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseError("An error occurred while $context"))
                    .build()
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

    /**
     * Populate the session DTO's primary recipient from its recipient Share. Recipients now
     * live on Share rows, not on the SharingSession entity itself.
     */
    private fun enrichSessionWithRecipient(sessionDto: SharingSessionDetailedDto?): SharingSessionDetailedDto?
    {
        if (sessionDto == null) return null
        if (sessionDto.recipient != null) return sessionDto
        val recipientUserId = shareService.primaryRecipientUserId(sessionDto.id) ?: return sessionDto
        val recipient = appUserService.getById(recipientUserId) ?: return sessionDto
        return sessionDto.copy(recipient = com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer.toDto(recipient))
    }

    /**
     * Populate the session DTO's document permission flags from the primary recipient share's
     * constraints JSON. The permissions live on the Share row, not on the SharingSession entity.
     *
     * Also parses the viewer-obligation keys (`watermark`, `max_views`, `require_mfa`)
     * so the viewer can apply a watermark overlay and hide the download button when denied.
     */
    private fun enrichSessionWithPermissions(sessionDto: SharingSessionDetailedDto?): SharingSessionDetailedDto?
    {
        if (sessionDto == null) return null
        val constraintsJson = shareService.recipientConstraintsJson(sessionDto.id) ?: return sessionDto
        val c = ShareConstraints.parse(constraintsJson)
        // The download key is `can_download` in the newer constraints but
        // `allow_document_download` in the legacy initiation flags — accept either.
        val downloadAllowed = c.canDownload != false &&
            (constraintsJson.contains("\"allow_document_download\":true") ||
                c.canDownload == true)
        return sessionDto.copy(
            allowDocumentAddition = c.allowDocumentAddition == true,
            allowDocumentDeletion = c.allowDocumentDeletion == true,
            allowDocumentDownload = downloadAllowed,
            allowDocumentUpdate = c.allowDocumentUpdate == true,
            allowDocumentUpload = c.allowDocumentUpload == true,
            watermark = c.watermark,
            maxViews = c.maxViews?.takeIf { it > 0 },
            requireMfa = c.requireMfa,
            allowedDownloadFormats = c.allowedDownloadFormats,
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