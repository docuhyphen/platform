package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer.Companion.toDto
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.InformationRequestCompletionDtoMapper
import com.docuhyphen.app.api.model.dto.ExchangeBasicDto
import com.docuhyphen.app.api.resource.exchange.operations.ExchangeResourceOperations
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.exchange.*
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityNotInstalledException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeCompletionException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS
import org.slf4j.LoggerFactory
import java.util.*

class ExchangeResource @Inject constructor(
    private val exchangeDocumentService: ExchangeDocumentService,
    private val exchangeInitiationService: ExchangeInitiationService,
    private val exchangeRetrievalService: ExchangeRetrievalService,
    private val exchangeUpdateService: ExchangeUpdateService,
    private val exchangeParticipantService: ExchangeParticipantService,
    private val shareQueryService: ShareQueryService,
    private val sessionAccessManagementService: ExchangeAccessManagementService,
    private val exchangeDetailsViewService: ExchangeDetailsViewService,
    private val exchangeWorkflowStatusService: ExchangeWorkflowStatusService,
) : ExchangeResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeResource::class.java)
    }

    override fun initiateExchange(exchangeInitiationDto: ExchangeInitiationDto): Response
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
                is InformationRequestTemplateVersionUnavailableException -> {
                    logger.warn(
                        "Exchange initiation refused because the Blueprint's Information Request Template Version " +
                                "cannot start a request: code={}",
                        exception.code,
                    )

                    Response.status(Response.Status.CONFLICT)
                        .entity(ResponseError(exception.message, exception.code))
                        .build()
                }

                is InformationRequestCapabilityNotInstalledException -> {
                    logger.warn(
                        "Exchange initiation refused because the Blueprint's Information Request needs capabilities " +
                                "this deployment does not serve: {}",
                        exception.unserved.joinToString { it.capability.name },
                    )

                    Response.status(Response.Status.CONFLICT)
                        .entity(
                            ResponseError(
                                exception.message,
                                InformationRequestErrorCatalog.CAPABILITY_NOT_INSTALLED
                            )
                        )
                        .build()
                }

                is InformationRequestLifecycleException -> {
                    logger.warn(
                        "Exchange initiation refused because the Blueprint's Information Request could not be created: " +
                                "reason={}",
                        exception.reasonCode,
                    )

                    Response.status(Response.Status.CONFLICT)
                        .entity(ResponseError(exception.message, exception.reasonCode))
                        .build()
                }

                is FieldValidationException,
                is IllegalArgumentException,
                is InvalidEmailException,
                is AppUserNotFoundException ->
                {
                    logger.error("Error initiating Exchange", exception)

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
                    logger.error("Error initiating Exchange", exception)

                    val responseError = ResponseError("An error occurred while initiating Exchange")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    override fun checkUserHasExchanges(): Response
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
            logger.error("Error checking if user has Exchanges", exception)
            val responseError = ResponseError("An error occurred while checking if user has Exchanges")
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
        }
    }

    override fun getExchanges(): Response
    {
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
                    logger.error("Error getting app user Exchanges", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error getting app user Exchanges", exception)

                    val responseError = ResponseError("An error occurred while getting app user Exchanges")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    override fun getExchange(exchangeId: String): Response
    {
        return try
        {
            val exchange = exchangeRetrievalService.getExchange(exchangeId)
            val exchangeDto = DetailedEntityToDtoTransformer.toDto(exchange)
            Response.ok(exchangeDetailsViewService.detailedView(exchangeDto)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException ->
                {
                    logger.error("Error getting Exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error getting Exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error getting Exchange", exception)

                    val responseError = ResponseError("An error occurred while getting Exchange")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    override fun getExchangeAccess(exchangeId: String): Response
    {
        return try
        {
            val view = sessionAccessManagementService.getSessionAccessView(UUID.fromString(exchangeId))
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
                    val responseError = ResponseError("Invalid Exchange id")
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error getting Exchange access view", exception)
                    val responseError = ResponseError("An error occurred while getting Exchange access")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    override fun grantExchangeAccess(
        exchangeId: String,
        request: GrantSessionShareRequest,
    ): Response
    {
        return try
        {
            val sessionUuid = UUID.fromString(exchangeId)
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
            mapAccessMutationError(exception, "granting Exchange access")
        }
    }

    override fun inviteTrustedParticipant(
        exchangeId: String,
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

    override fun resendPrimaryRecipientInvitation(
        exchangeId: String,
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

    override fun updateExchangeAccessRole(
        exchangeId: String,
        shareId: String,
        request: UpdateSessionShareRoleRequest,
    ): Response
    {
        return try
        {
            val sessionUuid = UUID.fromString(exchangeId)
            sessionAccessManagementService.changeRole(
                exchangeId = sessionUuid,
                shareId = UUID.fromString(shareId),
                roleName = request.roleName,
                constraintsJson = request.constraintsJson,
            )
            Response.ok(shareQueryService.getSessionAccessView(sessionUuid).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            mapAccessMutationError(exception, "updating Exchange access role")
        }
    }

    override fun revokeExchangeAccess(
        exchangeId: String,
        shareId: String,
    ): Response
    {
        return try
        {
            val sessionUuid = UUID.fromString(exchangeId)
            sessionAccessManagementService.revokeAccess(
                exchangeId = sessionUuid,
                shareId = UUID.fromString(shareId),
            )
            Response.ok(shareQueryService.getSessionAccessView(sessionUuid).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            mapAccessMutationError(exception, "revoking Exchange access")
        }
    }

    override fun replacePrimaryRecipient(
        exchangeId: String,
        request: ReplacePrimaryRecipientRequest,
    ): Response
    {
        return try
        {
            val sessionUuid = UUID.fromString(exchangeId)
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

    override fun updateExchange(
        exchangeId: String,
        request: UpdateExchangeRequest
    ): Response
    {
        return try
        {
            val exchangeDto = exchangeUpdateService.updateExchange(exchangeId, request)
            Response.ok(exchangeDetailsViewService.detailedView(exchangeDto)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is InformationRequestExchangeCompletionException ->
                {
                    logger.info(
                        "Exchange {} ending refused by its Information Requests: {}",
                        exchangeId,
                        exception.reasonCode
                    )
                    Response
                        .status(Response.Status.CONFLICT)
                        .entity(InformationRequestCompletionDtoMapper.refusal(exception))
                        .build()
                }

                is WorkflowConflictException ->
                {
                    logger.info("Workflow conflict on Exchange {} update: {}", exchangeId, exception.message)
                    Response
                        .status(Response.Status.CONFLICT)
                        .entity(ResponseError(exception.message, exception.reasonCode))
                        .build()
                }

                is ExchangeStatusTransitionException -> {
                    logger.info("Exchange {} status change refused: {}", exchangeId, exception.message)
                    Response
                        .status(Response.Status.CONFLICT)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is ExchangeNotFoundException ->
                {
                    logger.error("Error adding Exchange document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is ForbiddenException -> {
                    logger.warn("Exchange update refused: {}", exception.message)
                    Response
                        .status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error updating Exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error updating Exchange", exception)

                    val responseError = ResponseError("An error occurred while updating Exchange")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    override fun rescindExchange(exchangeId: String): Response
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
                    logger.error("Error rescinding Exchange", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while rescinding Exchange"))
                        .build()
                }
            }
        }
    }

    override fun issueSessionRecipientOtp(exchangeId: String): Response
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
                    logger.error("Error issuing recipient OTP for Exchange", exception)
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while sending access code"))
                        .build()
                }
            }
        }
    }

    override fun deleteExchange(exchangeId: String): Response
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
                    logger.error("Error deleting Exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error deleting Exchange", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error deleting Exchange", exception)

                    val responseError = ResponseError("An error occurred while deleting Exchange")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    override fun searchExchanges(
        query: String?,
        status: String?,
        initiatedBy: Boolean?,
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String
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
                    logger.error("Error searching Exchanges", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error searching Exchanges", exception)
                    val responseError = ResponseError("An error occurred while searching Exchanges")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    override fun getExchangeWorkflowInstances(exchangeId: String): Response
    {
        return try
        {
            val instances = exchangeWorkflowStatusService.workflowInstances(exchangeId)
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
                    logger.error("Error getting workflow instances for Exchange {}", exchangeId, exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while getting workflow instances"))
                        .build()
                }
            }
        }
    }

    override fun getExchangeWorkflowClearanceStatus(exchangeId: String): Response
    {
        return try
        {
            val status = exchangeWorkflowStatusService.clearanceStatus(exchangeId)
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

                is OrganizationContextRequiredException ->
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error getting workflow clearance status for Exchange {}", exchangeId, exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while getting clearance status"))
                        .build()
                }
            }
        }
    }

}
