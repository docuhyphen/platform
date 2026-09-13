package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestDelegatedAuthorityDtoMapper
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.model.GrantInformationRequestDelegatedAuthorityRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.RevokeInformationRequestDelegatedAuthorityRequest
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestDelegatedAuthorityResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestDelegatedAuthorityService
import com.docuhyphen.app.api.service.informationrequest.RevokeInformationRequestDelegatedAuthorityCommand
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CONFLICT
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.LoggerFactory
import java.util.UUID

@Path("/information-requests/{id}/delegated-authorities")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestDelegatedAuthorityResource @Inject constructor(
    private val delegatedAuthorityService: InformationRequestDelegatedAuthorityService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @POST
    fun grant(
        @PathParam("id") id: String,
        request: GrantInformationRequestDelegatedAuthorityRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
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
                        precondition = CommandPreconditionHeader.required(ifMatch),
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

    @POST
    @Path("/{authorityId}/revocations")
    fun revoke(
        @PathParam("id") id: String,
        @PathParam("authorityId") authorityId: String,
        request: RevokeInformationRequestDelegatedAuthorityRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
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
                        precondition = CommandPreconditionHeader.required(ifMatch),
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
        raw?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private fun badRequest(message: String): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(message)).build()

    private fun handleException(message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception
        return when (exception)
        {
            is CommandPreconditionException -> CommandPreconditionResponse.refused(exception)
            is CommandReceiptConflictException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()
            is IllegalStateException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message)).build()
            is IllegalArgumentException -> Response.status(NOT_FOUND)
                .entity(ResponseError(exception.message)).build()
            is ForbiddenException -> Response.status(FORBIDDEN)
                .entity(ResponseError(exception.message)).build()
            is UnauthorizedException -> Response.status(UNAUTHORIZED)
                .entity(ResponseError(exception.message)).build()
            else ->
            {
                logger.error(message, exception)
                Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
            }
        }
    }

    private companion object
    {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        val logger = LoggerFactory.getLogger(InformationRequestDelegatedAuthorityResource::class.java)
    }
}
