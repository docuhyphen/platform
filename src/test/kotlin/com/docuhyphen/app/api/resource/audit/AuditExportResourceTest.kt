package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.dto.AuditExportCreateRequestDto
import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportApproval
import com.docuhyphen.app.api.service.audit.AuditAccessAuthorizer
import com.docuhyphen.app.api.service.audit.export.AuditExportAccessService
import com.docuhyphen.app.api.service.audit.export.AuditExportService
import com.docuhyphen.app.api.service.audit.export.AuditIntegrityService
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class AuditExportResourceTest
{
    @Test
    fun `malformed export date returns bad request without invoking the service`()
    {
        val exportService = mock<AuditExportService>()
        val resource = AuditExportResource(
            auditExportAccessService = exportAccessService(exportService, PrincipalRef.user(UUID.randomUUID())),
            auditExportService = exportService,
            auditIntegrityService = mock<AuditIntegrityService>(),
        )

        val response = resource.requestPlatformExport(
            AuditExportCreateRequestDto(
                categories = listOf("SECURITY"),
                occurredAfter = "not-an-instant",
                occurredBefore = "2026-07-01T00:00:00Z",
                purpose = "investigation",
            ),
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(exportService, never()).requestExport(any(), any())
    }

    @Test
    fun `platform route lists approvals for a platform-scoped export`()
    {
        val exportId = UUID.randomUUID()
        val approverId = UUID.randomUUID()
        val principal = PrincipalRef.user(UUID.randomUUID())
        val exportService = mock<AuditExportService>()
        val export = AuditExport().apply { organizationId = null }
        val approval = AuditExportApproval().apply {
            approvedByUserId = approverId
            approvedAt = Timestamp.from(Instant.parse("2026-07-01T00:00:00Z"))
            note = "looks good"
        }
        whenever(exportService.getPlatformExport(exportId)).thenReturn(export)
        whenever(exportService.listApprovals(exportId)).thenReturn(listOf(approval))
        val resource = AuditExportResource(
            auditExportAccessService = exportAccessService(exportService, principal),
            auditExportService = exportService,
            auditIntegrityService = mock<AuditIntegrityService>(),
        )

        val response = resource.listPlatformExportApprovals(exportId.toString())

        assertEquals(Response.Status.OK.statusCode, response.status)
        @Suppress("UNCHECKED_CAST")
        val body = response.entity as List<com.docuhyphen.app.api.model.dto.AuditExportApprovalDto>
        assertEquals(1, body.size)
        assertEquals(approverId.toString(), body[0].approvedByUserId)
        assertEquals("looks good", body[0].note)
    }

    @Test
    fun `platform route hides approvals for an organization-scoped export`()
    {
        val exportId = UUID.randomUUID()
        val principal = PrincipalRef.user(UUID.randomUUID())
        val exportService = mock<AuditExportService>()
        whenever(exportService.getPlatformExport(exportId)).thenThrow(
            com.docuhyphen.app.api.service.audit.export.AuditExportNotFoundException(
                "Audit export not found"
            )
        )
        val resource = AuditExportResource(
            auditExportAccessService = exportAccessService(exportService, principal),
            auditExportService = exportService,
            auditIntegrityService = mock<AuditIntegrityService>(),
        )

        val response = resource.listPlatformExportApprovals(exportId.toString())

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
    }

    @Test
    fun `organization route hides a foreign export on read`()
    {
        val pathOrganizationId = UUID.randomUUID()
        val exportId = UUID.randomUUID()
        val exportService = foreignExportService(exportId, pathOrganizationId)
        val resource = organizationResource(exportService)

        val response = resource.getOrganizationExport(pathOrganizationId.toString(), exportId.toString())

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
    }

    @Test
    fun `organization route cannot approve a foreign export`()
    {
        val pathOrganizationId = UUID.randomUUID()
        val exportId = UUID.randomUUID()
        val exportService = foreignExportService(exportId, pathOrganizationId)
        val resource = organizationResource(exportService)

        val response = resource.approveOrganizationExport(
            pathOrganizationId.toString(),
            exportId.toString(),
            null,
        )

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        verify(exportService, never()).approveExport(any(), any(), any())
    }

    @Test
    fun `organization route cannot download a foreign export`()
    {
        val pathOrganizationId = UUID.randomUUID()
        val exportId = UUID.randomUUID()
        val exportService = foreignExportService(exportId, pathOrganizationId)
        val resource = organizationResource(exportService)

        val response = resource.downloadOrganizationExport(pathOrganizationId.toString(), exportId.toString())

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        verify(exportService, never()).downloadBundle(any(), any())
    }

    private fun foreignExportService(exportId: UUID, pathOrganizationId: UUID): AuditExportService =
        mock<AuditExportService>().also { service ->
            val export = AuditExport().apply {
                organizationId = UUID(
                    pathOrganizationId.mostSignificantBits xor 1L,
                    pathOrganizationId.leastSignificantBits,
                )
            }
            whenever(service.getExport(exportId)).thenReturn(export)
        }

    private fun organizationResource(exportService: AuditExportService): AuditOrganizationExportsResource
    {
        val principal = PrincipalRef.user(UUID.randomUUID())
        return AuditOrganizationExportsResource(
            auditExportAccessService = exportAccessService(exportService, principal),
            auditExportService = exportService,
        )
    }

    private fun exportAccessService(exportService: AuditExportService, principal: PrincipalRef): AuditExportAccessService =
        AuditExportAccessService(
            AuditAccessAuthorizer(allowingAuthorizationService(), authorizedContextFactory(principal)),
            exportService,
        )

    private fun authorizedContextFactory(principal: PrincipalRef): AuthorizationContextFactory =
        mock<AuthorizationContextFactory>().also { factory ->
            whenever(factory.currentPrincipal()).thenReturn(principal)
            whenever(factory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
        }

    private fun allowingAuthorizationService(): AuthorizationService =
        mock<AuthorizationService>().also { service ->
            whenever(service.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        }
}
