package com.docuhyphen.app.api.resource.informationrequest.noauth

import com.docuhyphen.app.api.model.InformationRequestAccessLinkDtoMapper
import com.docuhyphen.app.api.model.informationrequest.noauth.*
import com.docuhyphen.app.api.resource.command.CommandIdempotencyKeyHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.noauth.operations.InformationRequestAccessLinkResourceOperations
import com.docuhyphen.app.api.resource.model.IssueInformationRequestAccessLinkRequest
import com.docuhyphen.app.api.resource.model.ReplaceInformationRequestAccessLinkRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestBootstrapShareLinkService
import jakarta.inject.Inject
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CREATED
import org.slf4j.LoggerFactory
import java.util.*

class InformationRequestAccessLinkResource @Inject constructor(
    private val bootstrapShareLinkService: InformationRequestBootstrapShareLinkService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestAccessLinkResourceOperations
{
    override fun list(id: String): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            Response.ok(
                bootstrapShareLinkService.links(requestId, accessContextFactory.currentAuthenticated())
                    .map { InformationRequestAccessLinkDtoMapper.toDto(it.shareLink, it.partyId) }
                    .toTypedArray(),
            ).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request access link listing failed", exception)
        }
    }

    override fun issue(
        id: String,
        request: IssueInformationRequestAccessLinkRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            issued(
                bootstrapShareLinkService.issue(
                    IssueInformationRequestBootstrapShareLinkCommand(
                        requestId = requestId,
                        partyId = request.partyId,
                        access = accessContextFactory.currentAuthenticated(),
                        precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                        idempotencyKey = commandKey,
                        expiresAt = request.expiresAt,
                        maxUses = request.maxUses,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request access link issuance failed", exception)
        }
    }

    override fun rotate(
        id: String,
        shareLinkId: String,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val linkId = parseUuid(shareLinkId) ?: return badRequest("Invalid access link id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            rotated(
                bootstrapShareLinkService.rotate(
                    RotateInformationRequestBootstrapShareLinkCommand(
                        requestId = requestId,
                        shareLinkId = linkId,
                        access = accessContextFactory.currentAuthenticated(),
                        precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                        idempotencyKey = commandKey,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request access link rotation failed", exception)
        }
    }

    override fun replace(
        id: String,
        shareLinkId: String,
        request: ReplaceInformationRequestAccessLinkRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val linkId = parseUuid(shareLinkId) ?: return badRequest("Invalid access link id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            issued(
                bootstrapShareLinkService.replace(
                    ReplaceInformationRequestBootstrapShareLinkCommand(
                        requestId = requestId,
                        shareLinkId = linkId,
                        access = accessContextFactory.currentAuthenticated(),
                        precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                        idempotencyKey = commandKey,
                        expiresAt = request.expiresAt,
                        maxUses = request.maxUses,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request access link replacement failed", exception)
        }
    }

    override fun revoke(
        id: String,
        shareLinkId: String,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val linkId = parseUuid(shareLinkId) ?: return badRequest("Invalid access link id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            val revocation = bootstrapShareLinkService.revoke(
                RevokeInformationRequestBootstrapShareLinkCommand(
                    requestId = requestId,
                    shareLinkId = linkId,
                    access = accessContextFactory.currentAuthenticated(),
                    precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                    idempotencyKey = commandKey,
                ),
            )
            Response.ok(
                InformationRequestAccessLinkDtoMapper.toDto(revocation.shareLink, partyETag = revocation.partyETag),
            )
                .header(HttpHeaders.ETAG, revocation.partyETag)
                .build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request access link revocation failed", exception)
        }
    }

    private fun issued(issuance: InformationRequestBootstrapShareLinkIssuance): Response =
        Response.status(CREATED)
            .entity(InformationRequestAccessLinkDtoMapper.toIssuedDto(issuance))
            .header(HttpHeaders.ETAG, issuance.partyETag)
            .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
            .build()

    private fun rotated(issuance: InformationRequestBootstrapShareLinkIssuance): Response =
        Response.ok(InformationRequestAccessLinkDtoMapper.toIssuedDto(issuance))
            .header(HttpHeaders.ETAG, issuance.partyETag)
            .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
            .build()

    private fun requiredIdempotencyKey(raw: String?): String? =
        CommandIdempotencyKeyHeader.stated(raw)?.let(InformationRequestCommandHttp::idempotencyKey)

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private fun badRequest(message: String): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(message)).build()

    private fun handleException(message: String, exception: Exception): Response =
        InformationRequestCommandHttp.refused(logger, message, exception)

    private companion object
    {
        const val NO_STORE = "no-store"
        val logger = LoggerFactory.getLogger(InformationRequestAccessLinkResource::class.java)
    }
}
