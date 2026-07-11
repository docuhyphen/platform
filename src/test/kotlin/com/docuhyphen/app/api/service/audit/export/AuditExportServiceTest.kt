package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportApproval
import com.docuhyphen.app.api.model.entity.AuditExportStatus
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.repository.AuditExportApprovalRepository
import com.docuhyphen.app.api.repository.AuditExportRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveStorage
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.config.AuditExportConfigService
import com.docuhyphen.app.api.service.organization.OrganizationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Verifies [AuditExportService]:
 *  - a request goes to `APPROVAL_PENDING` when dual control is required, or straight to
 *    `BUILDING` when it is not.
 *  - the requester can never approve their own export (dual control cannot be bypassed by a
 *    single actor).
 *  - an export only reaches `BUILDING` once the configured number of distinct approvals lands.
 *  - a `READY` export cannot be downloaded past its expiry or its download limit.
 */
class AuditExportServiceTest
{
    private fun configService(dualControlRequired: Boolean = true, requiredApprovals: Int = 1): AuditExportConfigService
    {
        val config = mock<AuditExportConfigService>()
        whenever(config.isDualControlRequired()).thenReturn(dualControlRequired)
        whenever(config.getRequiredApprovals()).thenReturn(requiredApprovals)
        whenever(config.getMaxRangeDays()).thenReturn(366)
        whenever(config.getDownloadLifetimeHours()).thenReturn(72)
        whenever(config.getDefaultDownloadLimit()).thenReturn(5)
        return config
    }

    private fun service(
        exportRepo: AuditExportRepository = mock(),
        approvalRepo: AuditExportApprovalRepository = mock(),
        config: AuditExportConfigService = configService(),
    ): AuditExportService
    {
        val appUserService = mock<AppUserService>()
        whenever(appUserService.getById(any())).thenReturn(mock<AppUser>())
        val organizationService = mock<OrganizationService>()
        whenever(organizationService.getOrganizationById(any())).thenReturn(mock<Organization>())

        whenever(exportRepo.update(any())).thenAnswer { it.getArgument(0) }
        whenever(exportRepo.insert(any())).thenAnswer { it.getArgument(0) }

        return AuditExportService(
            exportRepo,
            approvalRepo,
            mock(),
            mock<AuditArchiveStorage>(),
            config,
            organizationService,
            appUserService,
            mock<AuditRecorder>(),
        )
    }

    private fun request(): AuditExportService.ExportRequest = AuditExportService.ExportRequest(
        organizationId = UUID.randomUUID(),
        categories = setOf(AuditCategory.EXCHANGE),
        occurredAfter = Instant.parse("2026-01-01T00:00:00Z"),
        occurredBefore = Instant.parse("2026-01-31T00:00:00Z"),
        purpose = "regulator inquiry",
    )

    @Test
    fun `requestExport with dual control required goes to APPROVAL_PENDING`()
    {
        val svc = service(config = configService(dualControlRequired = true))
        val export = svc.requestExport(request(), UUID.randomUUID())

        assertEquals(AuditExportStatus.APPROVAL_PENDING, export.status)
        assertEquals(1, export.requiredApprovals)
    }

    @Test
    fun `requestExport without dual control goes straight to BUILDING`()
    {
        val svc = service(config = configService(dualControlRequired = false))
        val export = svc.requestExport(request(), UUID.randomUUID())

        assertEquals(AuditExportStatus.BUILDING, export.status)
        assertEquals(0, export.requiredApprovals)
    }

    @Test
    fun `the requester cannot approve their own export`()
    {
        val requesterId = UUID.randomUUID()
        val exportRepo = mock<AuditExportRepository>()
        val approvalRepo = mock<AuditExportApprovalRepository>()
        val svc = service(exportRepo, approvalRepo)

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = requesterId
            status = AuditExportStatus.APPROVAL_PENDING
            requiredApprovals = 1
        }
        whenever(exportRepo.findById(export.id)).thenReturn(export)

        assertThrows(IllegalArgumentException::class.java) { svc.approveExport(export.id, requesterId) }
    }

    @Test
    fun `an export reaches BUILDING once the required number of distinct approvals lands`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val approvalRepo = mock<AuditExportApprovalRepository>()
        val svc = service(exportRepo, approvalRepo, configService(requiredApprovals = 2))

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = UUID.randomUUID()
            status = AuditExportStatus.APPROVAL_PENDING
            requiredApprovals = 2
        }
        whenever(exportRepo.findById(export.id)).thenReturn(export)
        whenever(approvalRepo.hasApprovalFrom(any(), any())).thenReturn(false)
        whenever(approvalRepo.insert(any())).thenAnswer { it.getArgument(0) }

        val afterFirst = svc.approveExport(export.id, UUID.randomUUID())
        assertEquals(AuditExportStatus.APPROVAL_PENDING, afterFirst.status)

        val afterSecond = svc.approveExport(export.id, UUID.randomUUID())
        assertEquals(AuditExportStatus.BUILDING, afterSecond.status)
    }

    @Test
    fun `a READY export past its expiry cannot be downloaded`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().minusSeconds(3600))
            downloadLimit = 5
        }
        whenever(exportRepo.findById(export.id)).thenReturn(export)

        assertThrows(AuditExportAccessException::class.java) { svc.recordDownload(export.id, UUID.randomUUID()) }
    }

    @Test
    fun `a READY export at its download limit cannot be downloaded again`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            downloadLimit = 2
            downloadCount = 2
        }
        whenever(exportRepo.findById(export.id)).thenReturn(export)

        assertThrows(AuditExportAccessException::class.java) { svc.recordDownload(export.id, UUID.randomUUID()) }
    }

    @Test
    fun `expireDue transitions past-expiry READY exports to EXPIRED`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().minusSeconds(60))
        }
        doReturn(listOf(export)).whenever(exportRepo).findDueForExpiry(any(), any())

        val count = svc.expireDue()

        assertEquals(1, count)
        assertEquals(AuditExportStatus.EXPIRED, export.status)
        assertTrue(true)
    }
}
