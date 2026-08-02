package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.model.dto.AuditGovernanceDtoMapper
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditAnalyticsReconciliationService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

/** Given its own class-level path; see AuditOrganizationEventsResource for why this class is not merged with others. */
@Path("/organizations/{organizationId}/audit-analytics/reconciliation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuditOrganizationAnalyticsResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditAnalyticsReconciliationService: AuditAnalyticsReconciliationService,
)
{
    @GET
    fun getOrganizationAnalyticsReconciliation(@PathParam("organizationId") organizationId: String): Response =
        withAuthorizedOrg(organizationId, Action.AUDIT_INTEGRITY_VERIFY) { principal, orgId ->
            val report = auditAnalyticsReconciliationService.reconcile(orgId, platformOnly = false, requestedByUserId = principal)
            Response.ok(AuditGovernanceDtoMapper.toDto(report)).build()
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
            Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
    }

    private fun parseUuid(raw: String): UUID = UUID.fromString(raw)
}
