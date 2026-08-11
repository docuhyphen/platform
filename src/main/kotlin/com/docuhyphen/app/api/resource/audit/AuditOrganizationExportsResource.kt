package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.AuditExportApprovalRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportDtoMapper
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditSearchProjectionService.AuditAccessActor
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.audit.export.AuditExportAccessException
import com.docuhyphen.app.api.service.audit.export.AuditExportNotFoundException
import com.docuhyphen.app.api.service.audit.export.AuditExportService
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
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

/** Given its own class-level path; see AuditOrganizationEventsResource for why this class is not merged with others. */
@Path("/organizations/{organizationId}/audit-exports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuditOrganizationExportsResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditExportService: AuditExportService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditOrganizationExportsResource::class.java)
    }
    @POST
    fun requestOrganizationExport(
        @PathParam("organizationId") organizationId: String,
        body: AuditExportCreateRequestDto,
    ): Response = withAuthorizedOrg(organizationId, Action.ORG_REQUEST_AUDIT_EXPORT) { actor, orgId ->
        val export = auditExportService.requestExport(toRequest(orgId, body), actor)
        Response.ok(AuditExportDtoMapper.toDto(export)).build()
    }

    @GET
    fun listOrganizationExports(@PathParam("organizationId") organizationId: String): Response =
        withAuthorizedOrg(organizationId, Action.ORG_READ_AUDIT) { _, orgId ->
            val exports = auditExportService.listForOrganization(orgId, platformOnly = false)
            Response.ok(exports.map(AuditExportDtoMapper::toDto)).build()
        }

    @GET
    @Path("/{exportId}")
    fun getOrganizationExport(
        @PathParam("organizationId") organizationId: String,
        @PathParam("exportId") exportId: String,
    ): Response = withAuthorizedOrg(organizationId, Action.ORG_READ_AUDIT) { _, orgId ->
        val export = auditExportService.getExport(parseUuid(exportId))
        requireOrgMatch(export.organizationId, orgId)
        Response.ok(AuditExportDtoMapper.toDto(export)).build()
    }

    @POST
    @Path("/{exportId}/approvals")
    fun approveOrganizationExport(
        @PathParam("organizationId") organizationId: String,
        @PathParam("exportId") exportId: String,
        body: AuditExportApprovalRequestDto?,
    ): Response = withAuthorizedOrg(organizationId, Action.AUDIT_EXPORT_APPROVE) { actor, orgId ->
        val id = parseUuid(exportId)
        requireOrgMatch(auditExportService.getExport(id).organizationId, orgId)
        val export = auditExportService.approveExport(id, actor.principal.id, body?.note)
        Response.ok(AuditExportDtoMapper.toDto(export)).build()
    }

    @GET
    @Path("/{exportId}/approvals")
    fun listOrganizationExportApprovals(
        @PathParam("organizationId") organizationId: String,
        @PathParam("exportId") exportId: String,
    ): Response = withAuthorizedOrg(organizationId, Action.ORG_READ_AUDIT) { _, orgId ->
        val id = parseUuid(exportId)
        requireOrgMatch(auditExportService.getExport(id).organizationId, orgId)
        Response.ok(auditExportService.listApprovals(id).map(AuditExportDtoMapper::toApprovalDto)).build()
    }

    @GET
    @Path("/{exportId}/file")
    @Produces("application/zip")
    fun downloadOrganizationExport(
        @PathParam("organizationId") organizationId: String,
        @PathParam("exportId") exportId: String,
    ): Response = withAuthorizedOrg(organizationId, Action.ORG_REQUEST_AUDIT_EXPORT) { actor, orgId ->
        val id = parseUuid(exportId)
        requireOrgMatch(auditExportService.getExport(id).organizationId, orgId)
        val bytes = auditExportService.downloadBundle(id, actor)
        Response.ok(bytes)
            .header("Content-Disposition", "attachment; filename=\"audit-export-$exportId.zip\"")
            .header("Content-Length", bytes.size)
            .build()
    }

    private fun toRequest(organizationId: UUID?, body: AuditExportCreateRequestDto): AuditExportService.ExportRequest =
        AuditExportService.ExportRequest(
            organizationId = organizationId,
            categories = body.categories.map { AuditCategory.valueOf(it.uppercase()) }.toSet(),
            occurredAfter = parseAuditInstant(body.occurredAfter, "occurredAfter"),
            occurredBefore = parseAuditInstant(body.occurredBefore, "occurredBefore"),
            purpose = body.purpose,
            caseReference = body.caseReference,
            legalBasis = body.legalBasis,
            downloadLimit = body.downloadLimit,
        )

    private fun requireOrgMatch(exportOrganizationId: UUID?, expectedOrganizationId: UUID?)
    {
        if (exportOrganizationId != expectedOrganizationId)
        {
            throw AuditExportNotFoundException("Audit export not found")
        }
    }

    private fun withAuthorizedOrg(
        organizationId: String,
        action: Action,
        block: (AuditAccessActor, UUID) -> Response,
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
            auditExportService.recordDeniedAttempt(principal.id, orgId, orgId.toString(), decision.reasonCode)
            return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
        }
        val capabilities = authorizationService.capabilities(principal, resource, context)
        val actor = AuditAccessActor(principal, context, capabilities)
        return runGuarded { block(actor, orgId) }
    }

    private fun runGuarded(block: () -> Response): Response
    {
        return try
        {
            block()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn("Organization audit-export request refused by subscription policy", e)
            throw e
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
