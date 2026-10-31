package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.creation.CreateAdHocInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromBlueprintCommand
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromTemplateVersionCommand
import com.docuhyphen.app.api.model.informationrequest.creation.InformationRequestCreationResult
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestLifecycleResult
import com.docuhyphen.app.api.model.informationrequest.lifecycle.IssueInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.SupersedeInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestWorkspacePurpose
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.informationrequest.operations.InformationRequestResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.template.InformationRequestTemplateRefusalResponse
import com.docuhyphen.app.api.resource.model.CancelInformationRequestRequest
import com.docuhyphen.app.api.resource.model.CreateInformationRequestDraftRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SupersedeInformationRequestRequest
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestHeaderProjectionService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityNotInstalledException
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestAdHocCreationService
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestBlueprintInstantiationService
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestTemplateInstantiationService
import com.docuhyphen.app.api.service.informationrequest.execution.RequestExecutionUsageExhaustedException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateValidationException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

class InformationRequestResource @Inject constructor(
    private val creationService: InformationRequestAdHocCreationService,
    private val lifecycleService: InformationRequestLifecycleService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
    private val responseWorkspaceService: InformationRequestResponseWorkspaceService,
    private val blueprintInstantiationService: InformationRequestBlueprintInstantiationService,
    private val templateInstantiationService: InformationRequestTemplateInstantiationService,
    private val headerProjection: InformationRequestHeaderProjectionService,
) : InformationRequestResourceOperations
{
    override fun get(id: String): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            Response.ok(responseWorkspaceService.loadRequest(requestId, accessContextFactory.currentAuthenticated()))
                .build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request lookup failed", exception)
        }
    }

    override fun responseWorkspace(id: String, purpose: String?): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            Response.ok(
                responseWorkspaceService.load(
                    requestId,
                    accessContextFactory.currentAuthenticated(),
                    workspacePurpose(purpose)
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request response workspace lookup failed", exception)
        }
    }

    override fun create(
        request: CreateInformationRequestDraftRequest,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            val sources = listOfNotNull(request.configuration, request.blueprintDefinitionId, request.templateVersionId)
            if (sources.size != 1)
            {
                return badRequest("State exactly one of configuration, blueprintDefinitionId, or templateVersionId")
            }
            val access = accessContextFactory.currentAuthenticated()
            val blueprintDefinitionId = request.blueprintDefinitionId
            val templateVersionId = request.templateVersionId
            created(
                access,
                when
                {
                    blueprintDefinitionId != null -> blueprintInstantiationService.createFromBlueprint(
                        CreateInformationRequestFromBlueprintCommand(
                            blueprintDefinitionId = blueprintDefinitionId,
                            exchangeId = request.exchangeId,
                            gatesExchangeClosure = request.gatesExchangeClosure,
                            access = access,
                            idempotencyKey = commandKey,
                        ),
                    )

                    templateVersionId != null -> templateInstantiationService.createFromTemplateVersion(
                        CreateInformationRequestFromTemplateVersionCommand(
                            templateVersionId = templateVersionId,
                            exchangeId = request.exchangeId,
                            gatesExchangeClosure = request.gatesExchangeClosure,
                            access = access,
                            idempotencyKey = commandKey,
                        ),
                    )

                    else -> creationService.createAdHoc(
                        CreateAdHocInformationRequestCommand(
                            exchangeId = request.exchangeId,
                            displayName = request.displayName.orEmpty(),
                            description = request.description,
                            configuration = requireNotNull(request.configuration),
                            gatesExchangeClosure = request.gatesExchangeClosure,
                            access = access,
                            idempotencyKey = commandKey,
                        ),
                    )
                },
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request draft creation failed", exception)
        }
    }

    override fun issue(
        id: String,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            val access = accessContextFactory.currentAuthenticated()
            ok(
                access,
                lifecycleService.issue(
                    IssueInformationRequestCommand(
                        requestId = requestId,
                        access = access,
                        precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                        idempotencyKey = commandKey,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request issuance failed", exception)
        }
    }

    override fun cancel(
        id: String,
        request: CancelInformationRequestRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            val access = accessContextFactory.currentAuthenticated()
            ok(
                access,
                lifecycleService.cancel(
                    CancelInformationRequestCommand(
                        requestId = requestId,
                        reasonCode = request?.reasonCode,
                        access = access,
                        precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                        idempotencyKey = commandKey,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request cancellation failed", exception)
        }
    }

    override fun supersede(
        id: String,
        request: SupersedeInformationRequestRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            val access = accessContextFactory.currentAuthenticated()
            ok(
                access,
                lifecycleService.supersede(
                    SupersedeInformationRequestCommand(
                        requestId = requestId,
                        supersededByRequestId = request.supersededByRequestId,
                        reasonCode = request.reasonCode,
                        access = access,
                        precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                        idempotencyKey = commandKey,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request supersession failed", exception)
        }
    }

    private fun created(access: RequestAccessContext, result: InformationRequestCreationResult): Response =
        Response.status(CREATED)
            .entity(headerProjection.headerOf(result.request, access))
            .header("ETag", result.requestETag)
            .build()

    private fun ok(access: RequestAccessContext, result: InformationRequestLifecycleResult): Response =
        Response.ok(headerProjection.headerOf(result.request, access))
            .header("ETag", result.requestETag)
            .build()

    private fun requiredIdempotencyKey(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let(InformationRequestCommandHttp::idempotencyKey)

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private fun workspacePurpose(raw: String?): InformationRequestWorkspacePurpose {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return InformationRequestWorkspacePurpose.RESPONSE
        return InformationRequestWorkspacePurpose.entries.firstOrNull { it.name == value.uppercase() }
            ?: throw InformationRequestCommandRequestException("Unknown workspace purpose: $value")
    }

    private fun badRequest(message: String): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(message)).build()

    private fun handleException(message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception

        return when (exception)
        {
            is CommandPreconditionException -> CommandPreconditionResponse.refused(exception)
            is CommandReceiptConflictException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()

            is InformationRequestCapabilityNotInstalledException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message, InformationRequestErrorCatalog.CAPABILITY_NOT_INSTALLED))
                .build()

            is InformationRequestCommandRequestException -> Response.status(BAD_REQUEST)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()

            is InformationRequestLifecycleException -> InformationRequestCommandHttp.lifecycleRefused(exception)

            is FieldValidationException -> InformationRequestCommandHttp.fieldValueRefused(exception)
            is FieldsPreconditionException -> InformationRequestCommandHttp.fieldsPreconditionRefused(exception)

            is InformationRequestTemplateValidationException -> InformationRequestTemplateRefusalResponse.of(exception)
            is InformationRequestTemplateVersionUnavailableException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message, exception.code)).build()

            is RequestExecutionUsageExhaustedException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.refusalMessage, InformationRequestErrorCatalog.CAPACITY_EXHAUSTED))
                .build()

            is IllegalStateException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message)).build()

            is IllegalArgumentException -> Response.status(NOT_FOUND)
                .entity(ResponseError(exception.message)).build()

            is ForbiddenException -> Response.status(FORBIDDEN)
                .entity(
                    ResponseError(
                        exception.message,
                        InformationRequestErrorCatalog.FORBIDDEN
                    )
                ).build()

            is UnauthorizedException -> Response.status(UNAUTHORIZED)
                .entity(ResponseError(exception.message)).build()

            else -> InformationRequestCommandHttp.storageRuleRefused(logger, message, exception) ?: run {
                logger.error(message, exception)
                Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
            }
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestResource::class.java)
    }
}
