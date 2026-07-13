package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.dto.AuditGovernanceDtoMapper
import com.docuhyphen.app.api.model.dto.AuditLegalHoldCreateRequestDto
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditLegalHoldService
import com.docuhyphen.app.api.service.audit.AuditLegalHoldNotFoundException
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

/** Given its own class-level path; see AuditOrganizationEventsResource for why this class is not merged with others. */
@Path("/organizations/{organizationId}/audit-legal-holds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuditOrganizationLegalHoldResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditLegalHoldService: AuditLegalHoldService,
)
{
    @POST
    fun placeOrganizationLegalHold(
        @PathParam("organizationId") organizationId: String,
        body: AuditLegalHoldCreateRequestDto,
    ): Response = withAuthorizedOrg(organizationId, Action.AUDIT_LEGAL_HOLD_MANAGE) { principal, orgId ->
        val hold = auditLegalHoldService.placeHold(orgId, body.resourceType, body.resourceId, body.reason, body.caseReference, principal)
        Response.ok(AuditGovernanceDtoMapper.toDto(hold)).build()
    }

    @GET
    fun listOrganizationLegalHolds(@PathParam("organizationId") organizationId: String): Response =
        withAuthorizedOrg(organizationId, Action.ORG_READ_AUDIT) { _, orgId ->
            Response.ok(auditLegalHoldService.listActiveHolds(orgId).map(AuditGovernanceDtoMapper::toDto)).build()
        }

    @POST
    @Path("/{holdId}/release")
    fun releaseOrganizationLegalHold(
        @PathParam("organizationId") organizationId: String,
        @PathParam("holdId") holdId: String,
    ): Response = withAuthorizedOrg(organizationId, Action.AUDIT_LEGAL_HOLD_MANAGE) { principal, orgId ->
        val hold = auditLegalHoldService.releaseHold(parseUuid(holdId), orgId, principal)
        Response.ok(AuditGovernanceDtoMapper.toDto(hold)).build()
    }

    private fun withAuthorizedOrg(
        organizationId: String,
        action: Action,
        block: (UUID, UUID) -> Response,
    ): Response
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        val context = authorizationContextFactory.currentContext()
        val orgId = parseUuid(organizationId)
        val decision = authorizationService.authorize(principal, action, ResourceRef.organization(orgId), context)
        if (decision is Decision.Deny)
        {
            auditLegalHoldService.recordDeniedAttempt(principal.id, orgId, decision.reasonCode)
            return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
        }
        return runGuarded { block(principal.id, orgId) }
    }

    private fun runGuarded(block: () -> Response): Response
    {
        return try
        {
            block()
        }
        catch (e: IllegalArgumentException)
        {
            val status = if (e is AuditLegalHoldNotFoundException) Response.Status.NOT_FOUND else Response.Status.BAD_REQUEST
            Response.status(status).entity(ResponseError(e.message)).build()
        }
    }

    private fun parseUuid(raw: String): UUID = UUID.fromString(raw)
}
