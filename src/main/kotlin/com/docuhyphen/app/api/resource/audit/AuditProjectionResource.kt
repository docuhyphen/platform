package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.model.dto.AuditProjectionDtoMapper
import com.docuhyphen.app.api.model.entity.ResourceType
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
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.Instant
import java.util.UUID

/**
 * The remaining two routes here never collide with another resource class's path, so they can
 * safely stay on a shared root path. Every other audit-events route that shares a leading
 * path-param segment with an unrelated pre-existing resource class has its own dedicated
 * resource (AuditOrganizationEventsResource, AuditExchangeEventsResource,
 * AuditExchangeDocumentEventsResource, AuditWorkflowDefinitionEventsResource,
 * AuditApplicationEventsResource, AuditSecurityIncidentEventsResource) to avoid RESTEasy
 * Reactive's cross-class routing collision.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuditProjectionResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditSearchProjectionService: AuditSearchProjectionService,
)
{
    @GET
    @Path("/users/me/security-events")
    fun listMySecurityEvents(
        @QueryParam("cursorOccurredAt") cursorOccurredAt: String?,
        @QueryParam("cursorEventId") cursorEventId: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
    ): Response
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        val context = authorizationContextFactory.currentContext()
        val capabilities = authorizationService.capabilities(principal, platformRef(), context)
        val page = auditSearchProjectionService.listPersonalSecurityEvents(
            actor = AuditSearchProjectionService.AuditAccessActor(principal, context, capabilities),
            cursor = parseCursor(cursorOccurredAt, cursorEventId),
            limit = limit.coerceIn(1, 200),
        )
        return Response.ok(AuditProjectionDtoMapper.toPageDto(page)).build()
    }

    @GET
    @Path("/platform/audit-events")
    fun listPlatformAuditEvents(
        @QueryParam("categories") categories: String?,
        @QueryParam("cursorOccurredAt") cursorOccurredAt: String?,
        @QueryParam("cursorEventId") cursorEventId: String?,
        @QueryParam("occurredAfter") occurredAfter: String?,
        @QueryParam("occurredBefore") occurredBefore: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
    ): Response = withAuthorizedPlatformAudit { actor ->
        val page = auditSearchProjectionService.listPlatformEvents(
            actor = actor,
            categories = parseCategories(categories),
            cursor = parseCursor(cursorOccurredAt, cursorEventId),
            limit = limit.coerceIn(1, 200),
            occurredAfter = occurredAfter?.let { parseAuditInstant(it, "occurredAfter") },
            occurredBefore = occurredBefore?.let { parseAuditInstant(it, "occurredBefore") },
        )
        Response.ok(AuditProjectionDtoMapper.toPageDto(page)).build()
    }

    private fun withAuthorizedPlatformAudit(
        block: (AuditSearchProjectionService.AuditAccessActor) -> Response,
    ): Response
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        val context = authorizationContextFactory.currentContext()
        val decision = authorizationService.authorize(principal, Action.APP_READ_AUDIT, platformRef(), context)
        val capabilities = authorizationService.capabilities(principal, platformRef(), context)
        val actor = AuditSearchProjectionService.AuditAccessActor(principal, context, capabilities)
        if (decision is Decision.Deny)
        {
            auditSearchProjectionService.recordDeniedAttempt(actor, null, "PLATFORM", "platform", decision.reasonCode)
            return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
        }
        return runGuarded { block(actor) }
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
            occurredAt = parseAuditInstant(cursorOccurredAt, "cursorOccurredAt"),
            eventId = parseUuid(cursorEventId),
        )
    }

    private fun parseUuid(raw: String): UUID = UUID.fromString(raw)

    private fun platformRef(): ResourceRef = ResourceRef(ResourceType.APPLICATION, UUID(0, 0))
}
