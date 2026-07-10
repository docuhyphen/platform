package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.dto.AuditExportCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportApprovalRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportDtoMapper
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.audit.export.AuditExportAccessException
import com.docuhyphen.app.api.service.audit.export.AuditExportNotFoundException
import com.docuhyphen.app.api.service.audit.export.AuditExportService
import com.docuhyphen.app.api.service.audit.export.AuditIntegrityService
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

/**
 * Thin REST adapter for the platform-scope half of Phase 6 (Verifiable Evidence Exports) of
 * `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`. All business logic, state-machine transitions, and
 * bundle construction live in [AuditExportService]/
 * [com.docuhyphen.app.api.service.audit.export.AuditExportBuilder]; this class only validates
 * input, authorizes, and maps results. The organization-scope routes live in
 * [AuditOrganizationExportsResource]/[AuditOrganizationIntegrityResource] instead of here, so that
 * no two resource classes share a leading path-param segment with different literal suffixes
 * (see AuditOrganizationEventsResource for why that matters).
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuditExportResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditExportService: AuditExportService,
    private val auditIntegrityService: AuditIntegrityService,
)
{
    @POST
    @Path("/platform/audit-exports")
    fun requestPlatformExport(body: AuditExportCreateRequestDto): Response =
        withAuthorizedPlatform(Action.APP_REQUEST_AUDIT_EXPORT) { principal ->
            val export = auditExportService.requestExport(toRequest(null, body), principal)
            Response.ok(AuditExportDtoMapper.toDto(export)).build()
        }

    @GET
    @Path("/platform/audit-exports")
    fun listPlatformExports(): Response = withAuthorizedPlatform(Action.APP_READ_AUDIT) { _ ->
        val exports = auditExportService.listForOrganization(null, platformOnly = true)
        Response.ok(exports.map(AuditExportDtoMapper::toDto)).build()
    }

    @GET
    @Path("/platform/audit-exports/{exportId}")
    fun getPlatformExport(@PathParam("exportId") exportId: String): Response =
        withAuthorizedPlatform(Action.APP_READ_AUDIT) { _ ->
            val export = auditExportService.getExport(parseUuid(exportId))
            requireOrgMatch(export.organizationId, null)
            Response.ok(AuditExportDtoMapper.toDto(export)).build()
        }

    @POST
    @Path("/platform/audit-exports/{exportId}/approvals")
    fun approvePlatformExport(@PathParam("exportId") exportId: String, body: AuditExportApprovalRequestDto?): Response =
        withAuthorizedPlatform(Action.AUDIT_EXPORT_APPROVE) { principal ->
            val id = parseUuid(exportId)
            requireOrgMatch(auditExportService.getExport(id).organizationId, null)
            val export = auditExportService.approveExport(id, principal, body?.note)
            Response.ok(AuditExportDtoMapper.toDto(export)).build()
        }

    @GET
    @Path("/platform/audit-exports/{exportId}/file")
    @Produces("application/zip")
    fun downloadPlatformExport(@PathParam("exportId") exportId: String): Response =
        withAuthorizedPlatform(Action.APP_REQUEST_AUDIT_EXPORT) { principal ->
            val id = parseUuid(exportId)
            requireOrgMatch(auditExportService.getExport(id).organizationId, null)
            val bytes = auditExportService.downloadBundle(id, principal)
            Response.ok(bytes)
                .header("Content-Disposition", "attachment; filename=\"audit-export-$exportId.zip\"")
                .header("Content-Length", bytes.size)
                .build()
        }

    @GET
    @Path("/platform/audit-integrity")
    fun getPlatformIntegrity(): Response = withAuthorizedPlatform(Action.APP_READ_AUDIT) { principal ->
        val report = auditIntegrityService.checkOrganization(null, platformOnly = true, requestedByUserId = principal)
        Response.ok(AuditExportDtoMapper.toIntegrityDto(report)).build()
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

    private fun withAuthorizedPlatform(action: Action, block: (UUID) -> Response): Response
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        val context = authorizationContextFactory.currentContext()
        val decision = authorizationService.authorize(principal, action, platformRef(), context)
        if (decision is Decision.Deny)
        {
            return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
        }
        return runGuarded { block(principal.id) }
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

    private fun platformRef(): ResourceRef = ResourceRef(ResourceType.APPLICATION, UUID(0, 0))
}
