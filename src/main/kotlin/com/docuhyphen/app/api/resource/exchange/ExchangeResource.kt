package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.NoAuthOtpException
import com.docuhyphen.app.api.exception.InvalidEmailException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.WorkflowConflictException
import com.docuhyphen.app.api.exception.OrganizationTrustException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer.Companion.toDto
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.DocumentDetailedDto
import com.docuhyphen.app.api.model.dto.ExchangeBasicDto
import com.docuhyphen.app.api.model.dto.ExchangeDetailedDto
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.resource.model.GrantSessionShareRequest
import com.docuhyphen.app.api.resource.model.InviteTrustedParticipantRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.ExchangeInitiationDto
import com.docuhyphen.app.api.resource.model.UpdateSessionShareRoleRequest
import com.docuhyphen.app.api.resource.model.ReplacePrimaryRecipientRequest
import com.docuhyphen.app.api.resource.model.UpdateExchangeRequest
import com.docuhyphen.app.api.service.exchange.*
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.workflow.WorkflowDefinitionService
import com.docuhyphen.app.api.service.storage.FileStorageService
import com.docuhyphen.app.api.service.auth.authz.ShareConstraints
import io.quarkus.security.ForbiddenException
import java.util.UUID
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS
import org.slf4j.LoggerFactory

@Path("/exchanges")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ExchangeResource @Inject constructor(
    private val exchangeDocumentService: ExchangeDocumentService,
    private val exchangeInitiationService: ExchangeInitiationService,
    private val exchangeRetrievalService: ExchangeRetrievalService,
    private val exchangeUpdateService: ExchangeUpdateService,
    private val exchangeParticipantService: ExchangeParticipantService,
    private val shareQueryService: ShareQueryService,
    private val shareService: ShareService,
    private val sessionAccessManagementService: ExchangeAccessManagementService,
    private val authTokenContext: AuthTokenContext,
    private val fileStorageService: FileStorageService,
    private val appUserService: AppUserService,
    private val principalGroupRepository: PrincipalGroupRepository,
    private val workflowDefinitionService: WorkflowDefinitionService,
    )
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeResource::class.java)
    }

    @POST
    fun initiateExchange(exchangeInitiationDto: ExchangeInitiationDto): Response
    {
        return try
        {
            val exchange = exchangeInitiationService.initiateExchange(exchangeInitiationDto)

            Response.ok(toDto(exchange)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is FieldValidationException,
                is IllegalArgumentException,
                is InvalidEmailException,
                is AppUserNotFoundException ->
                {
                    logger.error("Error initiating exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message))
                        .build()

                // A plan refusal carries the allowance and the plan that lifts it, so it is
                // rethrown for the dedicated mapper rather than flattened into a generic error.
                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Exchange initiation refused by the subscription plan check: reason={} plan={}",
                        exception.denial.reason,
                        exception.denial.planCode,
                    )

                    throw exception
                }

                else ->
                {
                    logger.error("Error initiating exchange", exception)

                    val responseError = ResponseError("An error occurred while initiating exchange")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @HEAD
    fun checkUserHasExchanges(): Response
    {
        return try
        {
            val hasSessions = exchangeRetrievalService.checkUserHasExchanges()
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
            logger.error("Error checking if user has exchanges", exception)
            val responseError = ResponseError("An error occurred while checking if user has exchanges")
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
        }
    }

    @GET
    fun getExchanges(): Response
    {
//        ResourceEndpointDelayHelper.delayEndpoint(1000, 3000)

        return try
        {
            val sessions = exchangeRetrievalService.getAllSessionsForSignedInAppUser()
            var sessionDTOs = arrayOf<ExchangeBasicDto?>()

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
                    logger.error("Error getting app user exchanges", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error getting app user exchanges", exception)

                    val responseError = ResponseError("An error occurred while getting app user exchanges")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @GET
    @Path("/{exchangeId}")
    fun getExchange(@PathParam("exchangeId") exchangeId: String): Response
    {
//        ResourceEndpointDelayHelper.delayEndpoint(2000, 4000)

        return try
        {
            val exchange = exchangeRetrievalService.getExchange(exchangeId)
            val exchangeDto = DetailedEntityToDtoTransformer.toDto(exchange)
            val enriched = enrichSessionWithRecipient(enrichSessionWithPermissions(enrichSessionWithFileSizes(exchangeDto)))
            Response.ok(enriched).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException ->
                {
                    logger.error("Error getting exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error getting exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error getting exchange", exception)

                    val responseError = ResponseError("An error occurred while getting exchange")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @GET
    @Path("/{exchangeId}/access")
    fun getExchangeAccess(@PathParam("exchangeId") exchangeId: String): Response
    {
        return try
        {
            val view = sessionAccessManagementService.getSessionAccessView(java.util.UUID.fromString(exchangeId))
            Response.ok(view.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException ->
                {
                    val responseError = ResponseError("Exchange not found")
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError("Invalid exchange id")
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error getting exchange access view", exception)
                    val responseError = ResponseError("An error occurred while getting exchange access")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @POST
    @Path("/{exchangeId}/access")
    fun grantExchangeAccess(
        @PathParam("exchangeId") exchangeId: String,
        request: GrantSessionShareRequest,
    ): Response
    {
        return try
        {
            val sessionUuid = java.util.UUID.fromString(exchangeId)
            sessionAccessManagementService.grantAccess(
                exchangeId = sessionUuid,
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
            mapAccessMutationError(exception, "granting exchange access")
        }
    }

    @POST
    @Path("/{exchangeId}/recipient-invitations")
    fun inviteTrustedParticipant(
        @PathParam("exchangeId") exchangeId: String,
        request: InviteTrustedParticipantRequest,
    ): Response
    {
        return try
        {
            val accessView = sessionAccessManagementService.inviteTrustedParticipant(
                exchangeId = UUID.fromString(exchangeId),
                selection = request.selection,
                roleName = request.roleName,
                constraintsJson = request.constraintsJson,
                expiresAtEpochMillis = request.expiresAtEpochMillis,
            )
            Response.ok(accessView.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            mapAccessMutationError(exception, "inviting a trusted Exchange participant")
        }
    }

    @POST
    @Path("/{exchangeId}/recipient-invitations/primary/resend")
    fun resendPrimaryRecipientInvitation(
        @PathParam("exchangeId") exchangeId: String,
    ): Response
    {
        return try
        {
            sessionAccessManagementService.resendNoAuthPrimaryRecipientInvitation(UUID.fromString(exchangeId))
            Response.status(Response.Status.NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            mapAccessMutationError(exception, "resending the primary recipient invitation")
        }
    }

    @PATCH
    @Path("/{exchangeId}/access/{shareId}")
    fun updateExchangeAccessRole(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("shareId") shareId: String,
        request: UpdateSessionShareRoleRequest,
    ): Response
    {
        return try
        {
            val sessionUuid = java.util.UUID.fromString(exchangeId)
            sessionAccessManagementService.changeRole(
                exchangeId = sessionUuid,
                shareId = java.util.UUID.fromString(shareId),
                roleName = request.roleName,
                constraintsJson = request.constraintsJson,
            )
            Response.ok(shareQueryService.getSessionAccessView(sessionUuid).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            mapAccessMutationError(exception, "updating exchange access role")
        }
    }

    @DELETE
    @Path("/{exchangeId}/access/{shareId}")
    fun revokeExchangeAccess(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("shareId") shareId: String,
    ): Response
    {
        return try
        {
            val sessionUuid = java.util.UUID.fromString(exchangeId)
            sessionAccessManagementService.revokeAccess(
                exchangeId = sessionUuid,
                shareId = java.util.UUID.fromString(shareId),
            )
            Response.ok(shareQueryService.getSessionAccessView(sessionUuid).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            mapAccessMutationError(exception, "revoking exchange access")
        }
    }

    @POST
    @Path("/{exchangeId}/access/primary-recipient")
    fun replacePrimaryRecipient(
        @PathParam("exchangeId") exchangeId: String,
        request: ReplacePrimaryRecipientRequest,
    ): Response
    {
        return try
        {
            val sessionUuid = java.util.UUID.fromString(exchangeId)
            val view = sessionAccessManagementService.replacePrimaryRecipient(
                exchangeId = sessionUuid,
                selection = request.selection,
            )
            Response.ok(view.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            mapAccessMutationError(exception, "replacing the primary recipient")
        }
    }

    private fun mapAccessMutationError(exception: Exception, context: String): Response =
        when (exception)
        {
            // A plan refusal carries the allowance and the plan that lifts it, so it is
            // rethrown for the dedicated mapper rather than flattened into a generic error.
            is SubscriptionDenialException ->
            {
                logger.warn(
                    "Refused by the subscription plan check while $context: reason={} plan={}",
                    exception.denial.reason,
                    exception.denial.planCode,
                )

                throw exception
            }

            is ForbiddenException ->
                Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

            is ExchangeNotFoundException ->
                Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

            is OrganizationTrustException ->
                Response.status(Response.Status.CONFLICT)
                    .entity(ResponseError("This trusted recipient can no longer be selected"))
                    .build()

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
    @Path("/{exchangeId}")
    fun updateExchange(
        @PathParam("exchangeId") exchangeId: String,
        request: UpdateExchangeRequest
    ): Response
    {
        return try
        {
            val exchangeDto = exchangeUpdateService.updateExchange(exchangeId, request)
            val enriched = enrichSessionWithRecipient(enrichSessionWithPermissions(enrichSessionWithFileSizes(exchangeDto)))
            Response.ok(enriched).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WorkflowConflictException ->
                {
                    logger.info("Workflow conflict on exchange {} update: {}", exchangeId, exception.message)
                    Response
                        .status(Response.Status.CONFLICT)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is ExchangeNotFoundException ->
                {
                    logger.error("Error adding exchange document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error updating exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error updating exchange", exception)

                    val responseError = ResponseError("An error occurred while updating exchange")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @POST
    @Path("/{exchangeId}/rescind")
    fun rescindExchange(@PathParam("exchangeId") exchangeId: String): Response
    {
        return try
        {
            exchangeUpdateService.rescindExchange(exchangeId)
            val exchange = exchangeRetrievalService.getExchange(exchangeId)
            Response.ok(DetailedEntityToDtoTransformer.toDto(exchange)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException ->
                {
                    Response.status(Response.Status.NOT_FOUND)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is ForbiddenException ->
                {
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is IllegalArgumentException ->
                {
                    Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                else ->
                {
                    logger.error("Error rescinding exchange", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while rescinding exchange"))
                        .build()
                }
            }
        }
    }

    @POST
    @Path("/{exchangeId}/recipient-otp")
    fun issueSessionRecipientOtp(@PathParam("exchangeId") exchangeId: String): Response
    {
        return try
        {
            exchangeUpdateService.issueRecipientOtp(exchangeId)
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

                is ExchangeNotFoundException ->
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
                        .status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                else ->
                {
                    logger.error("Error issuing recipient OTP for exchange", exception)
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while sending access code"))
                        .build()
                }
            }
        }
    }

    @DELETE
    @Path("/{exchangeId}")
    fun deleteExchange(@PathParam("exchangeId") exchangeId: String): Response
    {
        return try
        {
            exchangeUpdateService.deleteExchange(exchangeId)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException ->
                {
                    logger.error("Error deleting exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error deleting exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error deleting exchange", exception)

                    val responseError = ResponseError("An error occurred while deleting exchange")
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
    fun searchExchanges(
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

            val result = exchangeRetrievalService.searchExchanges(
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
                    logger.error("Error searching exchanges", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error searching exchanges", exception)
                    val responseError = ResponseError("An error occurred while searching exchanges")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @GET
    @Path("/{exchangeId}/workflow-instances")
    fun getExchangeWorkflowInstances(@PathParam("exchangeId") exchangeId: String): Response
    {
        return try
        {
            val id = UUID.fromString(exchangeId)
            // Gate on exchange membership; throws ExchangeNotFoundException for non-members.
            exchangeRetrievalService.getExchange(exchangeId)
            val callerId = authTokenContext.authToken.appUser?.id
            val callerOrgId = authTokenContext.activeOrganizationId
            // Users with no active org have no workflow context — return empty rather than
            // falling back to the no-filter query which would leak other orgs' instances.
            val instances = if (callerOrgId != null && callerId != null)
                workflowDefinitionService.listInstancesForSubject("EXCHANGE", id, callerOrgId, callerId)
            else
                emptyList()
            Response.ok(instances.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND)
                        .entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error getting workflow instances for exchange {}", exchangeId, exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while getting workflow instances"))
                        .build()
                }
            }
        }
    }

    @GET
    @Path("/{exchangeId}/workflow-clearance-status")
    fun getExchangeWorkflowClearanceStatus(@PathParam("exchangeId") exchangeId: String): Response
    {
        return try
        {
            val id = UUID.fromString(exchangeId)
            exchangeRetrievalService.getExchange(exchangeId)
            val callerOrgId = authTokenContext.activeOrganizationId
                ?: return Response.status(Response.Status.FORBIDDEN)
                    .entity(ResponseError("Organisation context required")).build()
            val status = workflowDefinitionService.getExchangeClearanceStatus(id, callerOrgId)
            Response.ok(status).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND)
                        .entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error getting workflow clearance status for exchange {}", exchangeId, exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while getting clearance status"))
                        .build()
                }
            }
        }
    }

    private fun enrichSessionWithFileSizes(sessionDto: ExchangeDetailedDto?): ExchangeDetailedDto?
    {
        if (sessionDto == null) return null

        return sessionDto.copy(
            documents = sessionDto.documents.map { document -> enrichDocumentWithFileSize(document) }
        )
    }

    /**
     * Populate the session DTO's primary recipient from its recipient Share. Recipients now
     * live on Share rows, not on the Exchange entity itself.
     */
    private fun enrichSessionWithRecipient(sessionDto: ExchangeDetailedDto?): ExchangeDetailedDto?
    {
        if (sessionDto == null) return null
        if (sessionDto.recipient != null) return sessionDto
        // Use display variants (search all share statuses) so ended/archived exchanges whose
        // shares are revoked still resolve a recipient for display rather than showing nothing.
        val recipientUserId = shareService.primaryRecipientUserIdForDisplay(sessionDto.id)
        if (recipientUserId != null)
        {
            val recipient = appUserService.getById(recipientUserId) ?: return sessionDto
            return sessionDto.copy(recipient = com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer.toPublicDto(recipient))
        }
        val recipientGroupId = shareService.primaryRecipientGroupIdForDisplay(sessionDto.id) ?: return sessionDto
        val groupName = principalGroupRepository.findById(recipientGroupId)?.name ?: return sessionDto
        return sessionDto.copy(recipientGroupName = groupName)
    }

    /**
     * Populate the session DTO's document permission flags from the primary recipient share's
     * constraints JSON. The permissions live on the Share row, not on the Exchange entity.
     *
     * Also parses the viewer-obligation keys (`watermark`, `require_mfa`)
     * so the viewer can apply a watermark overlay when required.
     */
    private fun enrichSessionWithPermissions(sessionDto: ExchangeDetailedDto?): ExchangeDetailedDto?
    {
        if (sessionDto == null) return null
        val constraintsJson = shareService.recipientConstraintsJson(sessionDto.id) ?: return sessionDto
        val c = ShareConstraints.parse(constraintsJson) ?: return sessionDto
        // The download key is `can_download` in the newer constraints but
        // `allow_document_download` in the legacy initiation flags, accept either.
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
