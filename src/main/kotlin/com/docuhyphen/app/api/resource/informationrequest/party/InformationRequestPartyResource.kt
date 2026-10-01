package com.docuhyphen.app.api.resource.informationrequest.party

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestPartyDtoMapper
import com.docuhyphen.app.api.model.informationrequest.party.AssignExternalParticipantInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestPartyAssignmentResult
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.party.operations.InformationRequestPartyResourceOperations
import com.docuhyphen.app.api.resource.model.AssignInformationRequestPartyRequest
import com.docuhyphen.app.api.resource.model.ReassignInformationRequestPartyRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

class InformationRequestPartyResource @Inject constructor(
    private val partyQueryService: InformationRequestPartyQueryService,
    private val partyService: InformationRequestPartyService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestPartyResourceOperations
{
    override fun list(id: String): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val listing = partyQueryService.listForManagement(requestId, accessContextFactory.currentAuthenticated())
            Response.ok(listing.parties.toTypedArray()).header("ETag", listing.partiesETag).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request party list failed", exception)
        }
    }

    override fun assign(
        id: String,
        request: AssignInformationRequestPartyRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = InformationRequestCommandHttp.uuid(id, "information request id")
            val named =
                listOfNotNull(request.userId, request.principalGroupId, request.email, request.subjectIdentityRefId)
            if (named.size != 1)
            {
                throw InformationRequestCommandRequestException(
                    "Name exactly one of userId, principalGroupId, email, or subjectIdentityRefId",
                )
            }
            val precondition = CommandPreconditionHeader.required(ifMatch)
            val commandKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val email = request.email
            val result = if (email != null)
                partyService.assignExternalParticipant(
                    AssignExternalParticipantInformationRequestPartyCommand(
                        requestId = requestId,
                        roleKey = request.roleKey,
                        email = email,
                        displayName = request.displayName,
                        access = access,
                        precondition = precondition,
                        idempotencyKey = commandKey,
                    ),
                )
            else
                partyService.assign(
                    AssignInformationRequestPartyCommand(
                        requestId = requestId,
                        roleKey = request.roleKey,
                        principal = request.userId?.let(PrincipalRef::user) ?: request.principalGroupId?.let(
                            PrincipalRef::group
                        ),
                        subjectIdentityRefId = request.subjectIdentityRefId,
                        exchangeRecipientId = request.exchangeRecipientId,
                        access = access,
                        precondition = precondition,
                        idempotencyKey = commandKey,
                    ),
                )
            Response.status(CREATED)
                .entity(InformationRequestPartyDtoMapper.toDto(result.party, revealIdentity = true))
                .header("ETag", result.partiesETag)
                .build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request party assignment failed", exception)
        }
    }

    override fun reassign(
        id: String,
        partyId: String,
        request: ReassignInformationRequestPartyRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            if (request.userId != null && request.principalGroupId != null)
            {
                throw InformationRequestCommandRequestException("Name only one of userId or principalGroupId")
            }
            val principal = request.userId?.let(PrincipalRef::user)
                ?: request.principalGroupId?.let(PrincipalRef::group)
                ?: throw InformationRequestCommandRequestException("Name the userId or principalGroupId to reassign to")
            val result = partyService.reassign(
                ReassignInformationRequestPartyCommand(
                    requestId = InformationRequestCommandHttp.uuid(id, "information request id"),
                    partyId = InformationRequestCommandHttp.uuid(partyId, "party id"),
                    principal = principal,
                    exchangeRecipientId = request.exchangeRecipientId,
                    access = accessContextFactory.currentAuthenticated(),
                    precondition = CommandPreconditionHeader.required(ifMatch),
                    idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
                ),
            )
            partyResponse(result)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request party reassignment failed", exception)
        }
    }

    override fun revoke(
        id: String,
        partyId: String,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val result = partyService.revoke(
                RevokeInformationRequestPartyCommand(
                    requestId = InformationRequestCommandHttp.uuid(id, "information request id"),
                    partyId = InformationRequestCommandHttp.uuid(partyId, "party id"),
                    access = accessContextFactory.currentAuthenticated(),
                    precondition = CommandPreconditionHeader.required(ifMatch),
                    idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
                ),
            )
            partyResponse(result)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request party revocation failed", exception)
        }
    }

    private fun partyResponse(result: InformationRequestPartyAssignmentResult): Response =
        Response.ok(InformationRequestPartyDtoMapper.toDto(result.party, revealIdentity = true))
            .header("ETag", result.partiesETag)
            .build()

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private fun badRequest(message: String): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(message)).build()

    private fun handleException(message: String, exception: Exception): Response =
        when (exception)
        {
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

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestPartyResource::class.java)
    }
}
