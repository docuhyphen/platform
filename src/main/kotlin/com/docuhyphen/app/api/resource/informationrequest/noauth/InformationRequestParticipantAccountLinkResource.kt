package com.docuhyphen.app.api.resource.informationrequest.noauth

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestParticipantAccountUpgradeDtoMapper
import com.docuhyphen.app.api.model.informationrequest.noauth.UpgradeInformationRequestParticipantAccountCommand
import com.docuhyphen.app.api.resource.command.CommandIdempotencyKeyHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.noauth.operations.InformationRequestParticipantAccountLinkResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpgradeInformationRequestParticipantAccountRequest
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestParticipantAccountUpgradeService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

class InformationRequestParticipantAccountLinkResource @Inject constructor(
    private val upgradeService: InformationRequestParticipantAccountUpgradeService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestParticipantAccountLinkResourceOperations
{
    override fun upgrade(
        id: String,
        request: UpgradeInformationRequestParticipantAccountRequest,
        ifMatch: String?,
        idempotencyKey: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            val upgrade = upgradeService.upgrade(
                UpgradeInformationRequestParticipantAccountCommand(
                    requestId = requestId,
                    sessionId = request.sessionId,
                    sessionCredential = sessionToken,
                    access = accessContextFactory.currentAuthenticated(),
                    precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                    idempotencyKey = commandKey,
                ),
            )
            Response.status(CREATED)
                .entity(InformationRequestParticipantAccountUpgradeDtoMapper.toDto(upgrade))
                .header(HttpHeaders.ETAG, upgrade.partyETag)
                .build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request participant account upgrade failed", exception)
        }
    }

    private fun requiredIdempotencyKey(raw: String?): String? =
        CommandIdempotencyKeyHeader.stated(raw)?.let(InformationRequestCommandHttp::idempotencyKey)

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

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

            is InformationRequestLifecycleException -> InformationRequestCommandHttp.lifecycleRefused(exception)

            is IllegalStateException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message)).build()

            is com.docuhyphen.app.api.exception.InformationRequestCommandRequestException -> Response.status(BAD_REQUEST)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()

            is IllegalArgumentException -> Response.status(NOT_FOUND)
                .entity(ResponseError(exception.message)).build()

            is ForbiddenException -> Response.status(FORBIDDEN)
                .entity(
                    ResponseError(
                        exception.message,
                        com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog.FORBIDDEN
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
        val logger = LoggerFactory.getLogger(InformationRequestParticipantAccountLinkResource::class.java)
    }
}
