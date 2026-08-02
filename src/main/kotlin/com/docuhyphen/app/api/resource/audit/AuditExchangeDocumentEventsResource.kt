package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.model.dto.AuditProjectionDtoMapper
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditProjectionAccessDeniedException
import com.docuhyphen.app.api.service.audit.AuditProjectionCursor
import com.docuhyphen.app.api.service.audit.AuditProjectionNotFoundException
import com.docuhyphen.app.api.service.audit.AuditSearchProjectionService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.Instant
import java.util.UUID

/** Given its own class-level path; see AuditOrganizationEventsResource for why this class is not merged with others. */
@Path("/exchanges/{exchangeId}/documents/{documentId}/audit-events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuditExchangeDocumentEventsResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val resourceAuthorizationContextRegistry: ResourceAuthorizationContextRegistry,
    private val auditSearchProjectionService: AuditSearchProjectionService,
)
{
    @GET
    fun listExchangeDocumentEvents(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
        @QueryParam("cursorOccurredAt") cursorOccurredAt: String?,
        @QueryParam("cursorEventId") cursorEventId: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
    ): Response
    {
        val exchangeRef = ResourceRef.exchange(parseUuid(exchangeId))
        val owner = resourceAuthorizationContextRegistry.resolve(exchangeRef)?.ownerContext
            ?: return Response.status(Response.Status.NOT_FOUND).entity(ResponseError("Exchange not found")).build()
        if (owner !is OwnerContext.Organization)
        {
            return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Exchange audit is not available for this scope")).build()
        }
        return withAuthorizedOrgAudit(owner.organizationId.toString()) { actor, orgId ->
            val page = auditSearchProjectionService.listExchangeDocumentEvents(
                actor = actor,
                organizationId = orgId,
                exchangeId = exchangeRef.id,
                documentId = parseUuid(documentId),
                cursor = parseCursor(cursorOccurredAt, cursorEventId),
                limit = limit.coerceIn(1, 200),
            )
            Response.ok(AuditProjectionDtoMapper.toPageDto(page)).build()
        }
    }

    private fun withAuthorizedOrgAudit(
        organizationId: String,
        block: (AuditSearchProjectionService.AuditAccessActor, UUID) -> Response,
    ): Response
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        val context = authorizationContextFactory.currentContext()
        val orgId = parseUuid(organizationId)
        val resource = ResourceRef.organization(orgId)
        val decision = authorizationService.authorize(principal, Action.ORG_READ_AUDIT, resource, context)
        val capabilities = authorizationService.capabilities(principal, resource, context)
        val actor = AuditSearchProjectionService.AuditAccessActor(principal, context, capabilities)
        if (decision is Decision.Deny)
        {
            auditSearchProjectionService.recordDeniedAttempt(actor, orgId, "ORGANIZATION", orgId.toString(), decision.reasonCode)
            return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
        }
        return runGuarded { block(actor, orgId) }
    }

    private fun runGuarded(block: () -> Response): Response
    {
        return try
        {
            block()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
        catch (e: AuditProjectionNotFoundException)
        {
            Response.status(Response.Status.NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: AuditProjectionAccessDeniedException)
        {
            Response.status(Response.Status.FORBIDDEN).entity(ResponseError(e.message)).build()
        }
    }

    private fun parseCursor(cursorOccurredAt: String?, cursorEventId: String?): AuditProjectionCursor?
    {
        if (cursorOccurredAt.isNullOrBlank() || cursorEventId.isNullOrBlank())
        {
            return null
        }
        return AuditProjectionCursor(
            occurredAt = Instant.parse(cursorOccurredAt),
            eventId = parseUuid(cursorEventId),
        )
    }

    private fun parseUuid(raw: String): UUID = UUID.fromString(raw)
}
