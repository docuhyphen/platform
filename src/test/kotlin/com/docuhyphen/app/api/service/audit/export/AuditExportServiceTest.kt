package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuditEngagementSensitivity
import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportApproval
import com.docuhyphen.app.api.model.entity.AuditExportStatus
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.repository.AuditExportApprovalRepository
import com.docuhyphen.app.api.repository.AuditExportRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.audit.AuditEngagementService
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.AuditSearchProjectionService.AuditAccessActor
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveStorage
import com.docuhyphen.app.api.service.audit.archive.MerkleTree
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.config.AuditExportConfigService
import com.docuhyphen.app.api.service.organization.OrganizationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Duration
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
        engagementService: AuditEngagementService = mock(),
        archiveStorage: AuditArchiveStorage = mock(),
        auditRecorder: AuditRecorder = mock(),
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
            archiveStorage,
            config,
            organizationService,
            appUserService,
            engagementService,
            auditRecorder,
            mock(),
            mock(),
        )
    }

    private fun request(organizationId: UUID? = UUID.randomUUID()): AuditExportService.ExportRequest = AuditExportService.ExportRequest(
        organizationId = organizationId,
        categories = setOf(AuditCategory.EXCHANGE),
        occurredAfter = Instant.parse("2026-01-01T00:00:00Z"),
        occurredBefore = Instant.parse("2026-01-31T00:00:00Z"),
        purpose = "regulator inquiry",
    )

    private fun actor(vararg capabilities: Capability, mfaSatisfied: Boolean = true, principalId: UUID = UUID.randomUUID()): AuditAccessActor = AuditAccessActor(
        principal = PrincipalRef.user(principalId),
        context = AuthorizationContext(mfaSatisfied = mfaSatisfied),
        capabilities = capabilities.toSet(),
    )

    /** An organization owner/admin: holds direct governance capability, so no engagement is required. */
    private fun governanceActor(mfaSatisfied: Boolean = true, principalId: UUID = UUID.randomUUID()): AuditAccessActor =
        actor(Capability.ORG_POLICY_MANAGE, Capability.ORG_AUDIT_EXPORT, mfaSatisfied = mfaSatisfied, principalId = principalId)

    /** A platform administrator: platform-scope exports (organizationId == null) always bypass the engagement gate. */
    private fun platformAdminActor(mfaSatisfied: Boolean = true, principalId: UUID = UUID.randomUUID()): AuditAccessActor =
        actor(Capability.APP_ADMIN, Capability.APP_AUDIT_EXPORT, mfaSatisfied = mfaSatisfied, principalId = principalId)

    /** A plain platform auditor: no APP_ADMIN, no ORG_AUDIT_VIEW_SENSITIVE equivalent at platform scope. */
    private fun platformAuditorActor(mfaSatisfied: Boolean = true, principalId: UUID = UUID.randomUUID()): AuditAccessActor =
        actor(Capability.APP_AUDIT_EXPORT, mfaSatisfied = mfaSatisfied, principalId = principalId)

    /** Stubs only the sensitivity-required call shape the service is expected to make; any other invocation shape falls through to Mockito's default null return. */
    private fun permissiveEngagementService(exportPermitted: Boolean = true): AuditEngagementService
    {
        val engagementService = mock<AuditEngagementService>()
        whenever(
            engagementService.resolveAccess(
                principalUserId = any(),
                organizationId = anyOrNull(),
                resourceType = anyOrNull(),
                resourceId = anyOrNull(),
                category = any(),
                requireSensitive = eq(true),
                recentStepUpSatisfied = anyOrNull(),
                requestedRange = anyOrNull(),
            ),
        ).thenReturn(
            AuditEngagementService.EngagementAccess(
                engagementId = UUID.randomUUID(),
                sensitivityLevel = AuditEngagementSensitivity.SENSITIVE,
                exportPermitted = exportPermitted,
                maxQueryRangeDays = null,
                downloadLimit = null,
            ),
        )
        return engagementService
    }

    @Test
    fun `export range is bounded before persistence work begins`()
    {
        val repository = mock<AuditExportRepository>()
        val oversized = request().copy(
            occurredAfter = Instant.parse("2025-01-01T00:00:00Z"),
            occurredBefore = Instant.parse("2026-01-03T00:00:00Z"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            service(exportRepo = repository).requestExport(oversized, governanceActor())
        }

        verify(repository, never()).insert(any())
    }

    @Test
    fun `requestExport with dual control required goes to APPROVAL_PENDING`()
    {
        val svc = service(config = configService(dualControlRequired = true))
        val export = svc.requestExport(request(), governanceActor())

        assertEquals(AuditExportStatus.APPROVAL_PENDING, export.status)
        assertEquals(1, export.requiredApprovals)
    }

    @Test
    fun `requestExport without dual control goes straight to BUILDING`()
    {
        val svc = service(config = configService(dualControlRequired = false))
        val export = svc.requestExport(request(), governanceActor())

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
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        assertThrows(AuditExportAccessException::class.java) { svc.approveExport(export.id, requesterId) }
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
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)
        whenever(approvalRepo.hasApprovalFrom(any(), any())).thenReturn(false)
        whenever(approvalRepo.insert(any())).thenAnswer { it.getArgument(0) }
        whenever(approvalRepo.countByExport(export.id)).thenReturn(1L, 2L)

        val afterFirst = svc.approveExport(export.id, UUID.randomUUID())
        assertEquals(AuditExportStatus.APPROVAL_PENDING, afterFirst.status)

        val afterSecond = svc.approveExport(export.id, UUID.randomUUID())
        assertEquals(AuditExportStatus.BUILDING, afterSecond.status)
    }

    /**
     * Proves the fix for the approval-count lost-update race: two concurrent `approveExport` calls
     * that both observe the export row before either commits would, under the old
     * `export.approvalCount += 1` logic, both compute `1` and the transition to `BUILDING` would
     * never happen even though two distinct approvers had in fact approved. `findByIdForUpdate`
     * (a real Postgres `SELECT ... FOR UPDATE`) serializes the two transactions so the second one
     * re-reads under the lock; recomputing `approvalCount` from the append-only approval table
     * (rather than trusting whatever was in memory when the row was locked) then reflects the true
     * total regardless of what the in-memory counter held before the lock was acquired. This is a
     * pure-logic unit test - the mocked repository stands in for the row lock whose real
     * serialization is enforced by Postgres - so it proves the recompute-after-lock behavior, not
     * the database's lock semantics.
     */
    @Test
    fun `approveExport recomputes approvalCount from the append-only approval table instead of an in-memory increment`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val approvalRepo = mock<AuditExportApprovalRepository>()
        val svc = service(exportRepo, approvalRepo, configService(requiredApprovals = 2))

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = UUID.randomUUID()
            status = AuditExportStatus.APPROVAL_PENDING
            requiredApprovals = 2
            approvalCount = 0
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)
        whenever(approvalRepo.hasApprovalFrom(any(), any())).thenReturn(false)
        whenever(approvalRepo.insert(any())).thenAnswer { it.getArgument(0) }
        // A second, concurrent approver's row already landed in the append-only table by the time
        // this transaction acquires the lock, even though the in-memory export still holds the
        // stale approvalCount = 0 it had before the lock.
        whenever(approvalRepo.countByExport(export.id)).thenReturn(2L)

        val result = svc.approveExport(export.id, UUID.randomUUID())

        assertEquals(2, result.approvalCount)
        assertEquals(AuditExportStatus.BUILDING, result.status)
    }

    @Test
    fun `approveExport locks the export row with findByIdForUpdate rather than a plain read`()
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
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)
        whenever(approvalRepo.hasApprovalFrom(any(), any())).thenReturn(false)
        whenever(approvalRepo.insert(any())).thenAnswer { it.getArgument(0) }
        whenever(approvalRepo.countByExport(export.id)).thenReturn(1L)

        svc.approveExport(export.id, UUID.randomUUID())

        verify(exportRepo).findByIdForUpdate(export.id)
        verify(exportRepo, never()).findById(export.id)
    }

    @Test
    fun `a READY export past its expiry cannot be downloaded`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)
        val requester = governanceActor()

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = requester.principal.id
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().minusSeconds(3600))
            downloadLimit = 5
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        assertThrows(AuditExportAccessException::class.java) { svc.recordDownload(export.id, requester) }
    }

    @Test
    fun `a READY export at its download limit cannot be downloaded again`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)
        val requester = governanceActor()

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = requester.principal.id
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            downloadLimit = 2
            downloadCount = 2
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        assertThrows(AuditExportAccessException::class.java) { svc.recordDownload(export.id, requester) }
    }

    /**
     * Proves `recordDownload` locks the export row (a real Postgres `SELECT ... FOR UPDATE` in
     * production, via `findByIdForUpdate`) before checking and incrementing `downloadCount`, rather
     * than reading with a plain `findById`. Locking serializes concurrent downloads of the same
     * export so a second transaction re-reads the already-incremented count instead of racing
     * against a stale in-memory value, which is what makes the download-limit check-then-increment
     * atomic across concurrent requests. As with the approval-count test above, this is a
     * pure-logic unit test proving the lock is taken and the limit is re-checked under it, not
     * Postgres's own lock serialization (no real concurrent-transaction harness exists in this
     * repository yet).
     */
    @Test
    fun `recordDownload locks the export row with findByIdForUpdate rather than a plain read`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)
        val requester = governanceActor()

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = requester.principal.id
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            downloadLimit = 5
            downloadCount = 4
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        val downloaded = svc.recordDownload(export.id, requester)

        assertEquals(5, downloaded.downloadCount)
        verify(exportRepo).findByIdForUpdate(export.id)
        verify(exportRepo, never()).findById(export.id)
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
        doReturn(listOf(export)).whenever(exportRepo).findDueForExpiryForUpdate(any(), any())

        val count = svc.expireDue()

        assertEquals(1, count)
        assertEquals(AuditExportStatus.EXPIRED, export.status)
        assertTrue(true)
    }

    /**
     * Verifies the multi-worker build-claim lease: `claimForBuilding` locks the export row, then
     * re-checks under the lock whether it is still `BUILDING` and whether any existing lease has
     * expired, before writing a new lease. This is what lets more than one application node run
     * the build scheduler at once without two nodes performing the same export's archive I/O. As
     * with the approval/download-lock tests above, this proves the lock-then-recheck decision
     * logic with a mocked repository standing in for the row lock; no real multi-transaction
     * Postgres harness exists in this repository yet.
     */
    @Test
    fun `claimForBuilding claims a BUILDING export with no existing lease`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)
        val export = AuditExport().apply {
            id = UUID.randomUUID()
            status = AuditExportStatus.BUILDING
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        val claimed = svc.claimForBuilding(export.id, "worker-a", Duration.ofMinutes(10))

        assertEquals("worker-a", claimed?.buildWorkerId)
        assertNotNull(claimed?.buildLeaseExpiresAt)
        verify(exportRepo).update(export)
    }

    @Test
    fun `claimForBuilding refuses an export already leased by another worker within the lease window`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)
        val export = AuditExport().apply {
            id = UUID.randomUUID()
            status = AuditExportStatus.BUILDING
            buildWorkerId = "worker-a"
            buildLeaseExpiresAt = Timestamp.from(Instant.now().plusSeconds(300))
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        val claimed = svc.claimForBuilding(export.id, "worker-b", Duration.ofMinutes(10))

        assertNull(claimed)
        assertEquals("worker-a", export.buildWorkerId)
        verify(exportRepo, never()).update(any())
    }

    @Test
    fun `claimForBuilding reclaims an export whose lease has expired`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)
        val export = AuditExport().apply {
            id = UUID.randomUUID()
            status = AuditExportStatus.BUILDING
            buildWorkerId = "worker-a"
            buildLeaseExpiresAt = Timestamp.from(Instant.now().minusSeconds(60))
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        val claimed = svc.claimForBuilding(export.id, "worker-b", Duration.ofMinutes(10))

        assertEquals("worker-b", claimed?.buildWorkerId)
    }

    @Test
    fun `claimForBuilding does not claim an export that is no longer BUILDING`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)
        val export = AuditExport().apply {
            id = UUID.randomUUID()
            status = AuditExportStatus.READY
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        val claimed = svc.claimForBuilding(export.id, "worker-a", Duration.ofMinutes(10))

        assertNull(claimed)
        verify(exportRepo, never()).update(any())
    }

    @Test
    fun `requestExport is denied for an organization auditor with no export-permitted engagement`()
    {
        val engagementService = mock<AuditEngagementService>()
        whenever(
            engagementService.resolveAccess(
                principalUserId = any(),
                organizationId = anyOrNull(),
                resourceType = anyOrNull(),
                resourceId = anyOrNull(),
                category = any(),
                requireSensitive = any(),
                recentStepUpSatisfied = anyOrNull(),
                requestedRange = anyOrNull(),
            ),
        ).thenReturn(null)
        val svc = service(engagementService = engagementService)

        assertThrows(AuditExportAccessException::class.java) {
            svc.requestExport(request(), actor(Capability.ORG_AUDIT_EXPORT))
        }
    }

    @Test
    fun `requestExport is denied for an organization auditor whose matching engagement does not permit export`()
    {
        val svc = service(engagementService = permissiveEngagementService(exportPermitted = false))

        assertThrows(AuditExportAccessException::class.java) {
            svc.requestExport(request(), actor(Capability.ORG_AUDIT_EXPORT))
        }
    }

    @Test
    fun `requestExport succeeds for an organization auditor with an active export-permitted engagement`()
    {
        val svc = service(engagementService = permissiveEngagementService(exportPermitted = true))

        val export = svc.requestExport(request(), actor(Capability.ORG_AUDIT_EXPORT))

        assertEquals(AuditExportStatus.APPROVAL_PENDING, export.status)
    }

    @Test
    fun `requestExport does not require an engagement for a caller holding ORG_POLICY_MANAGE`()
    {
        val engagementService = mock<AuditEngagementService>()
        val svc = service(engagementService = engagementService)

        val export = svc.requestExport(request(), governanceActor())

        assertEquals(AuditExportStatus.APPROVAL_PENDING, export.status)
        verifyNoInteractions(engagementService)
    }

    @Test
    fun `recordDownload is denied at download time when no engagement still permits export`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val engagementService = mock<AuditEngagementService>()
        whenever(
            engagementService.resolveAccess(
                principalUserId = any(),
                organizationId = anyOrNull(),
                resourceType = anyOrNull(),
                resourceId = anyOrNull(),
                category = any(),
                requireSensitive = any(),
                recentStepUpSatisfied = anyOrNull(),
                requestedRange = anyOrNull(),
            ),
        ).thenReturn(null)
        val svc = service(exportRepo, engagementService = engagementService)
        val requester = actor(Capability.ORG_AUDIT_EXPORT)

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = requester.principal.id
            organizationId = UUID.randomUUID()
            categoriesCsv = AuditCategory.EXCHANGE.name
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            downloadLimit = 5
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        assertThrows(AuditExportAccessException::class.java) {
            svc.recordDownload(export.id, requester)
        }
    }

    @Test
    fun `recordDownload succeeds at download time for an organization auditor with a still-active export-permitted engagement`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo, engagementService = permissiveEngagementService(exportPermitted = true))
        val requester = actor(Capability.ORG_AUDIT_EXPORT)

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = requester.principal.id
            organizationId = UUID.randomUUID()
            categoriesCsv = AuditCategory.EXCHANGE.name
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            downloadLimit = 5
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        val downloaded = svc.recordDownload(export.id, requester)

        assertEquals(1, downloaded.downloadCount)
    }

    @Test
    fun `requestExport is denied for an organization governance caller without recent step-up`()
    {
        val svc = service()

        assertThrows(AuditExportAccessException::class.java) {
            svc.requestExport(request(), governanceActor(mfaSatisfied = false))
        }
    }

    @Test
    fun `requestExport is denied for a platform-scope caller without recent step-up`()
    {
        val svc = service()

        assertThrows(AuditExportAccessException::class.java) {
            svc.requestExport(request(organizationId = null), platformAuditorActor(mfaSatisfied = false))
        }
    }

    @Test
    fun `requestExport succeeds for a platform-scope caller with recent step-up`()
    {
        val svc = service()

        val export = svc.requestExport(request(organizationId = null), platformAuditorActor(mfaSatisfied = true))

        assertEquals(AuditExportStatus.APPROVAL_PENDING, export.status)
    }

    @Test
    fun `requestExport succeeds for a platform administrator with recent step-up`()
    {
        val svc = service()

        val export = svc.requestExport(request(organizationId = null), platformAdminActor(mfaSatisfied = true))

        assertEquals(AuditExportStatus.APPROVAL_PENDING, export.status)
    }

    @Test
    fun `requestExport is denied for an organization auditor whose matching engagement is not export-permitted at sensitive level`()
    {
        val engagementService = mock<AuditEngagementService>()
        whenever(
            engagementService.resolveAccess(
                principalUserId = any(),
                organizationId = anyOrNull(),
                resourceType = anyOrNull(),
                resourceId = anyOrNull(),
                category = any(),
                requireSensitive = eq(false),
                recentStepUpSatisfied = anyOrNull(),
                requestedRange = anyOrNull(),
            ),
        ).thenReturn(
            AuditEngagementService.EngagementAccess(
                engagementId = UUID.randomUUID(),
                sensitivityLevel = AuditEngagementSensitivity.STANDARD,
                exportPermitted = true,
                maxQueryRangeDays = null,
                downloadLimit = null,
            ),
        )
        val svc = service(engagementService = engagementService)

        assertThrows(AuditExportAccessException::class.java) {
            svc.requestExport(request(), actor(Capability.ORG_AUDIT_EXPORT))
        }
    }

    @Test
    fun `recordDownload is denied for a caller who is not the original requester and holds no custodian capability`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo, engagementService = permissiveEngagementService(exportPermitted = true))

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = UUID.randomUUID()
            organizationId = UUID.randomUUID()
            categoriesCsv = AuditCategory.EXCHANGE.name
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            downloadLimit = 5
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        assertThrows(AuditExportAccessException::class.java) {
            svc.recordDownload(export.id, actor(Capability.ORG_AUDIT_EXPORT))
        }
    }

    @Test
    fun `recordDownload succeeds for an organization governance user downloading another user's export`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = UUID.randomUUID()
            organizationId = UUID.randomUUID()
            categoriesCsv = AuditCategory.EXCHANGE.name
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            downloadLimit = 5
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        val downloaded = svc.recordDownload(export.id, governanceActor())

        assertEquals(1, downloaded.downloadCount)
    }

    @Test
    fun `recordDownload succeeds for an APP_ADMIN downloading another user's platform export`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = UUID.randomUUID()
            organizationId = null
            categoriesCsv = AuditCategory.EXCHANGE.name
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            downloadLimit = 5
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        val downloaded = svc.recordDownload(export.id, platformAdminActor())

        assertEquals(1, downloaded.downloadCount)
    }

    @Test
    fun `recordDownload is denied for a platform auditor downloading another user's platform export`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val svc = service(exportRepo)

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = UUID.randomUUID()
            organizationId = null
            categoriesCsv = AuditCategory.EXCHANGE.name
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            downloadLimit = 5
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)

        assertThrows(AuditExportAccessException::class.java) {
            svc.recordDownload(export.id, platformAuditorActor())
        }
    }

    /**
     * [AuditExportService.downloadBundle] recomputes the fetched object's digest and compares it
     * against [AuditExport.bundleDigest] recorded when the bundle was built, so a bundle object
     * modified in storage after being marked `READY` is refused at the very last step before
     * evidence leaves the system, not merely trusted because the lifecycle row still says `READY`.
     */
    @Test
    fun `downloadBundle returns the bytes when the fetched object matches its recorded digest`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val archiveStorage = mock<AuditArchiveStorage>()
        val svc = service(exportRepo, engagementService = permissiveEngagementService(exportPermitted = true), archiveStorage = archiveStorage)
        val requester = actor(Capability.ORG_AUDIT_EXPORT)
        val bundleBytes = "genuine-bundle-bytes".toByteArray()

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = requester.principal.id
            organizationId = UUID.randomUUID()
            categoriesCsv = AuditCategory.EXCHANGE.name
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            downloadLimit = 5
            bundleObjectKey = "exports/${id}/bundle.zip"
            bundleDigest = MerkleTree.sha256Hex(bundleBytes)
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)
        whenever(archiveStorage.getObject(export.bundleObjectKey!!)).thenReturn(bundleBytes)

        val downloaded = svc.downloadBundle(export.id, requester)

        assertTrue(bundleBytes.contentEquals(downloaded))
    }

    @Test
    fun `downloadBundle refuses to serve a bundle object that no longer matches its recorded digest`()
    {
        val exportRepo = mock<AuditExportRepository>()
        val archiveStorage = mock<AuditArchiveStorage>()
        val auditRecorder = mock<AuditRecorder>()
        val svc = service(
            exportRepo,
            engagementService = permissiveEngagementService(exportPermitted = true),
            archiveStorage = archiveStorage,
            auditRecorder = auditRecorder,
        )
        val requester = actor(Capability.ORG_AUDIT_EXPORT)

        val export = AuditExport().apply {
            id = UUID.randomUUID()
            requestedByUserId = requester.principal.id
            organizationId = UUID.randomUUID()
            categoriesCsv = AuditCategory.EXCHANGE.name
            status = AuditExportStatus.READY
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
            downloadLimit = 5
            bundleObjectKey = "exports/${id}/bundle.zip"
            bundleDigest = MerkleTree.sha256Hex("genuine-bundle-bytes".toByteArray())
        }
        whenever(exportRepo.findByIdForUpdate(export.id)).thenReturn(export)
        whenever(exportRepo.findById(export.id)).thenReturn(export)
        whenever(archiveStorage.getObject(export.bundleObjectKey!!)).thenReturn("tampered-bundle-bytes".toByteArray())

        assertThrows(AuditExportIntegrityFailedException::class.java) {
            svc.downloadBundle(export.id, requester)
        }
        // Once for the AUDIT_EXPORT_DOWNLOADED attempt recordDownload already committed, once more
        // for the AUDIT_EXPORT_DOWNLOAD_INTEGRITY_FAILED event recorded when the digest mismatch
        // was discovered immediately afterward.
        verify(auditRecorder, org.mockito.kotlin.times(2)).record(any())
    }
}
