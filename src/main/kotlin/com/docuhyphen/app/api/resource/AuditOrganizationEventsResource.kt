package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.dto.AuditProjectionDtoMapper
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditProjectionAccessDeniedException
import com.docuhyphen.app.api.service.audit.AuditProjectionCursor
import com.docuhyphen.app.api.service.audit.AuditProjectionNotFoundException
import com.docuhyphen.app.api.service.audit.AuditSearchProjectionService
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
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

/**
 * Given its own dedicated class-level path (rather than sharing a root "/" path with full
 * absolute paths at the method level), this resource cannot collide with any other resource
 * class that also serves under "/organizations/{organizationId}/..." - RESTEasy Reactive's
 * routing has been observed to silently drop a route when two different resource classes
 * declare method-level paths with the same leading param position but different literal
 * suffixes (see AUDIT-ARCHITECTURE-IMPLEMENTATION.md handoff notes for the reproduction).
 */
@Path("/organizations/{organizationId}/audit-events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuditOrganizationEventsResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditSearchProjectionService: AuditSearchProjectionService,
)
{
    @GET
    fun listOrganizationEvents(
        @PathParam("organizationId") organizationId: String,
        @QueryParam("categories") categories: String?,
        @QueryParam("cursorOccurredAt") cursorOccurredAt: String?,
        @QueryParam("cursorEventId") cursorEventId: String?,
        @QueryParam("occurredAfter") occurredAfter: String?,
        @QueryParam("occurredBefore") occurredBefore: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
    ): Response = withAuthorizedOrgAudit(organizationId) { actor, orgId ->
        val page = auditSearchProjectionService.listOrganizationEvents(
            actor = actor,
            organizationId = orgId,
            categories = parseCategories(categories),
            cursor = parseCursor(cursorOccurredAt, cursorEventId),
            limit = limit.coerceIn(1, 200),
            occurredAfter = occurredAfter?.let(Instant::parse),
            occurredBefore = occurredBefore?.let(Instant::parse),
        )
        Response.ok(AuditProjectionDtoMapper.toPageDto(page)).build()
    }

    @GET
    @Path("/{eventId}")
    fun getOrganizationEvent(
        @PathParam("organizationId") organizationId: String,
        @PathParam("eventId") eventId: String,
    ): Response = withAuthorizedOrgAudit(organizationId) { actor, orgId ->
        val event = auditSearchProjectionService.getOrganizationEvent(
            actor = actor,
            organizationId = orgId,
            eventId = parseUuid(eventId),
        )
        Response.ok(AuditProjectionDtoMapper.toEventDto(event)).build()
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

    private fun parseCategories(raw: String?): Set<AuditCategory>
    {
        if (raw.isNullOrBlank())
        {
            return emptySet()
        }
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { AuditCategory.valueOf(it.uppercase()) }
            .toSet()
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
