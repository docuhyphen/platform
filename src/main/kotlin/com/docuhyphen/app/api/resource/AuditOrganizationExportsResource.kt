package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.dto.AuditExportApprovalRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportDtoMapper
import com.docuhyphen.app.api.resource.model.ResponseError
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
    @POST
    fun requestOrganizationExport(
        @PathParam("organizationId") organizationId: String,
        body: AuditExportCreateRequestDto,
    ): Response = withAuthorizedOrg(organizationId, Action.ORG_REQUEST_AUDIT_EXPORT) { principal, orgId ->
        val export = auditExportService.requestExport(toRequest(orgId, body), principal)
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
    ): Response = withAuthorizedOrg(organizationId, Action.AUDIT_EXPORT_APPROVE) { principal, orgId ->
        val id = parseUuid(exportId)
        requireOrgMatch(auditExportService.getExport(id).organizationId, orgId)
        val export = auditExportService.approveExport(id, principal, body?.note)
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
    ): Response = withAuthorizedOrg(organizationId, Action.ORG_REQUEST_AUDIT_EXPORT) { principal, orgId ->
        val id = parseUuid(exportId)
        requireOrgMatch(auditExportService.getExport(id).organizationId, orgId)
        val bytes = auditExportService.downloadBundle(id, principal)
        Response.ok(bytes)
            .header("Content-Disposition", "attachment; filename=\"audit-export-$exportId.zip\"")
            .header("Content-Length", bytes.size)
            .build()
    }

    private fun toRequest(organizationId: UUID?, body: AuditExportCreateRequestDto): AuditExportService.ExportRequest =
        AuditExportService.ExportRequest(
            organizationId = organizationId,
            categories = body.categories.map { AuditCategory.valueOf(it.uppercase()) }.toSet(),
            occurredAfter = Instant.parse(body.occurredAfter),
            occurredBefore = Instant.parse(body.occurredBefore),
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
