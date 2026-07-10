package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.dto.AuditExportDtoMapper
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.export.AuditExportAccessException
import com.docuhyphen.app.api.service.audit.export.AuditExportNotFoundException
import com.docuhyphen.app.api.service.audit.export.AuditIntegrityService
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
@Path("/organizations/{organizationId}/audit-integrity")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuditOrganizationIntegrityResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditIntegrityService: AuditIntegrityService,
)
{
    @GET
    fun getOrganizationIntegrity(@PathParam("organizationId") organizationId: String): Response =
        withAuthorizedOrg(organizationId, Action.ORG_READ_AUDIT) { principal, orgId ->
            val report = auditIntegrityService.checkOrganization(orgId, platformOnly = false, requestedByUserId = principal)
            Response.ok(AuditExportDtoMapper.toIntegrityDto(report)).build()
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
        val resource = ResourceRef.organization(orgId)
        val decision = authorizationService.authorize(principal, action, resource, context)
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
        catch (e: AuditExportNotFoundException)
        {
            Response.status(Response.Status.NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: AuditExportAccessException)
        {
            Response.status(Response.Status.FORBIDDEN).entity(ResponseError(e.message)).build()
        }
    }

    private fun parseUuid(raw: String): UUID = UUID.fromString(raw)
}
