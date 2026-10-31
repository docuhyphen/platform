package com.docuhyphen.app.api.resource.informationrequest.party

import com.docuhyphen.app.api.model.InformationRequestDelegatedAuthorityDtoMapper
import com.docuhyphen.app.api.model.informationrequest.party.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestDelegatedAuthorityResult
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.resource.command.CommandIdempotencyKeyHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.party.operations.InformationRequestDelegatedAuthorityResourceOperations
import com.docuhyphen.app.api.resource.model.GrantInformationRequestDelegatedAuthorityRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.RevokeInformationRequestDelegatedAuthorityRequest
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestDelegatedAuthorityService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CREATED
import org.slf4j.LoggerFactory
import java.util.*

class InformationRequestDelegatedAuthorityResource @Inject constructor(
    private val delegatedAuthorityService: InformationRequestDelegatedAuthorityService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestDelegatedAuthorityResourceOperations
{
    override fun list(id: String): Response {
        return try {
            val requestId = parseUuid(id)
                ?: return badRequest("Invalid information request id")
            val listed = delegatedAuthorityService.list(requestId, accessContextFactory.currentAuthenticated())
            Response.ok(listed.authorities.map(InformationRequestDelegatedAuthorityDtoMapper::toDto).toTypedArray())
                .header("ETag", listed.authoritiesETag)
                .build()
        } catch (exception: Exception) {
            handleException("Information Request delegated authority listing failed", exception)
        }
    }

    override fun grant(
        id: String,
        request: GrantInformationRequestDelegatedAuthorityRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id)
                ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            created(
                delegatedAuthorityService.grant(
                    GrantInformationRequestDelegatedAuthorityCommand(
                        requestId = requestId,
                        assignedPartyId = request.assignedPartyId,
                        delegatePrincipal = PrincipalRef(
                            request.delegatePrincipalKind,
                            request.delegatePrincipalId,
                        ),
                        requirementId = request.requirementId,
                        authorityInstrumentRef = request.authorityInstrumentRef,
                        effectiveAt = request.effectiveAt,
                        expiresAt = request.expiresAt,
                        access = accessContextFactory.currentAuthenticated(),
                        precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                        idempotencyKey = commandKey,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request delegated authority grant failed", exception)
        }
    }

    override fun revoke(
        id: String,
        authorityId: String,
        request: RevokeInformationRequestDelegatedAuthorityRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id)
                ?: return badRequest("Invalid information request id")
            val parsedAuthorityId = parseUuid(authorityId)
                ?: return badRequest("Invalid delegated authority id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            ok(
                delegatedAuthorityService.revoke(
                    RevokeInformationRequestDelegatedAuthorityCommand(
                        requestId = requestId,
                        authorityId = parsedAuthorityId,
                        reason = request?.reason,
                        access = accessContextFactory.currentAuthenticated(),
                        precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                        idempotencyKey = commandKey,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request delegated authority revocation failed", exception)
        }
    }

    private fun created(result: InformationRequestDelegatedAuthorityResult): Response =
        Response.status(CREATED)
            .entity(InformationRequestDelegatedAuthorityDtoMapper.toDto(result.authority))
            .header("ETag", result.authoritiesETag)
            .build()

    private fun ok(result: InformationRequestDelegatedAuthorityResult): Response =
        Response.ok(InformationRequestDelegatedAuthorityDtoMapper.toDto(result.authority))
            .header("ETag", result.authoritiesETag)
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
        val logger = LoggerFactory.getLogger(InformationRequestDelegatedAuthorityResource::class.java)
    }
}
