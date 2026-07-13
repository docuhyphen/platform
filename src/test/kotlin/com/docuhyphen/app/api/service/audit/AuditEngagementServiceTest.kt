package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuditEngagement
import com.docuhyphen.app.api.model.entity.AuditEngagementSensitivity
import com.docuhyphen.app.api.model.entity.AuditEngagementStatus
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.repository.AuditEngagementRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.auth.StepUpAuthService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AuditEngagementServiceTest
{
    private fun activeEngagement(
        startsAt: Instant = Instant.now().minusSeconds(60),
        expiresAt: Instant = Instant.now().plusSeconds(60),
    ): AuditEngagement = AuditEngagement().apply {
        status = AuditEngagementStatus.ACTIVE
        this.startsAt = Timestamp.from(startsAt)
        this.expiresAt = Timestamp.from(expiresAt)
        categoriesCsv = AuditCategory.DOCUMENT.name
        sensitivityLevel = AuditEngagementSensitivity.STANDARD
    }

    @Test
    fun `access validity uses request time rather than historical event time`()
    {
        val principalId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val repository = mock<AuditEngagementRepository>()
        whenever(repository.findActiveForPrincipal(any(), any(), any(), any(), any(), any()))
            .thenReturn(
                listOf(activeEngagement())
            )
        val principalGroupService = mock<PrincipalGroupService>()
        whenever(principalGroupService.getActiveGroupIdsForPrincipal(any(), eq(principalId)))
            .thenReturn(emptySet())
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            mock<AppUserService>(),
            mock<OrganizationGroupService>(),
            mock<OrganizationMembershipService>(),
            principalGroupService,
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )
        val before = Instant.now()

        val access = service.resolveAccess(
            principalUserId = principalId,
            organizationId = organizationId,
            resourceType = "Document",
            resourceId = UUID.randomUUID().toString(),
            category = AuditCategory.DOCUMENT,
            requireSensitive = false,
            requestedRange = Duration.ofDays(1),
        )

        assertNotNull(access)
        val capturedNow = argumentCaptor<Timestamp>()
        verify(repository).findActiveForPrincipal(
            eq(organizationId),
            eq("Document"),
            any(),
            eq(principalId),
            eq(emptySet()),
            capturedNow.capture(),
        )
        val after = Instant.now()
        assertFalse(capturedNow.firstValue.toInstant().isBefore(before))
        assertFalse(capturedNow.firstValue.toInstant().isAfter(after))
    }

    @Test
    fun `expired engagement is denied while scheduler status is still active`()
    {
        val principalId = UUID.randomUUID()
        val repository = mock<AuditEngagementRepository>()
        whenever(repository.findActiveForPrincipal(any(), any(), any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    activeEngagement(
                        startsAt = Instant.now().minusSeconds(120),
                        expiresAt = Instant.now().minusSeconds(60),
                    )
                )
            )
        val principalGroupService = mock<PrincipalGroupService>()
        whenever(principalGroupService.getActiveGroupIdsForPrincipal(any(), eq(principalId)))
            .thenReturn(emptySet())
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            mock<AppUserService>(),
            mock<OrganizationGroupService>(),
            mock<OrganizationMembershipService>(),
            principalGroupService,
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )

        val access = service.resolveAccess(
            principalUserId = principalId,
            organizationId = UUID.randomUUID(),
            resourceType = "Document",
            resourceId = UUID.randomUUID().toString(),
            category = AuditCategory.DOCUMENT,
            requireSensitive = false,
        )

        assertNull(access)
    }

    @Test
    fun `sensitive engagement is not resolved without recent step-up even for a standard read`()
    {
        val principalId = UUID.randomUUID()
        val engagement = activeEngagement().apply {
            sensitivityLevel = AuditEngagementSensitivity.SENSITIVE
        }
        val repository = mock<AuditEngagementRepository>()
        whenever(repository.findActiveForPrincipal(any(), any(), any(), any(), any(), any()))
            .thenReturn(listOf(engagement))
        val principalGroupService = mock<PrincipalGroupService>()
        whenever(principalGroupService.getActiveGroupIdsForPrincipal(any(), eq(principalId)))
            .thenReturn(emptySet())
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            mock<AppUserService>(),
            mock<OrganizationGroupService>(),
            mock<OrganizationMembershipService>(),
            principalGroupService,
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )

        val access = service.resolveAccess(
            principalUserId = principalId,
            organizationId = UUID.randomUUID(),
            resourceType = "DOCUMENT",
            resourceId = "doc-A",
            category = AuditCategory.DOCUMENT,
            requireSensitive = false,
            recentStepUpSatisfied = false,
        )

        assertNull(access)
    }

    @Test
    fun `resource-scoped engagement does not authorize a different resource`()
    {
        val principalId = UUID.randomUUID()
        val engagement = activeEngagement().apply {
            resourceType = "DOCUMENT"
            resourceId = "doc-A"
        }
        val repository = mock<AuditEngagementRepository>()
        whenever(repository.findActiveForPrincipal(any(), any(), any(), any(), any(), any()))
            .thenReturn(listOf(engagement))
        val principalGroupService = mock<PrincipalGroupService>()
        whenever(principalGroupService.getActiveGroupIdsForPrincipal(any(), eq(principalId)))
            .thenReturn(emptySet())
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            mock<AppUserService>(),
            mock<OrganizationGroupService>(),
            mock<OrganizationMembershipService>(),
            principalGroupService,
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )

        val access = service.resolveAccess(
            principalUserId = principalId,
            organizationId = UUID.randomUUID(),
            resourceType = "DOCUMENT",
            resourceId = "doc-B",
            category = AuditCategory.DOCUMENT,
            requireSensitive = false,
        )

        assertNull(access)
    }

    @Test
    fun `resource-scoped engagement does not authorize an event with no resource reference`()
    {
        val principalId = UUID.randomUUID()
        val engagement = activeEngagement().apply {
            resourceType = "DOCUMENT"
            resourceId = "doc-A"
        }
        val repository = mock<AuditEngagementRepository>()
        whenever(repository.findActiveForPrincipal(any(), anyOrNull(), anyOrNull(), any(), any(), any()))
            .thenReturn(listOf(engagement))
        val principalGroupService = mock<PrincipalGroupService>()
        whenever(principalGroupService.getActiveGroupIdsForPrincipal(any(), eq(principalId)))
            .thenReturn(emptySet())
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            mock<AppUserService>(),
            mock<OrganizationGroupService>(),
            mock<OrganizationMembershipService>(),
            principalGroupService,
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )

        val access = service.resolveAccess(
            principalUserId = principalId,
            organizationId = UUID.randomUUID(),
            resourceType = null,
            resourceId = null,
            category = AuditCategory.DOCUMENT,
            requireSensitive = false,
        )

        assertNull(access)
    }

    @Test
    fun `query range cannot exceed engagement maximum by a partial day`()
    {
        val principalId = UUID.randomUUID()
        val engagement = activeEngagement().apply { maxQueryRangeDays = 7 }
        val repository = mock<AuditEngagementRepository>()
        whenever(repository.findActiveForPrincipal(any(), any(), any(), any(), any(), any()))
            .thenReturn(listOf(engagement))
        val principalGroupService = mock<PrincipalGroupService>()
        whenever(principalGroupService.getActiveGroupIdsForPrincipal(any(), eq(principalId)))
            .thenReturn(emptySet())
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            mock<AppUserService>(),
            mock<OrganizationGroupService>(),
            mock<OrganizationMembershipService>(),
            principalGroupService,
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )

        val access = service.resolveAccess(
            principalUserId = principalId,
            organizationId = UUID.randomUUID(),
            resourceType = "Document",
            resourceId = UUID.randomUUID().toString(),
            category = AuditCategory.DOCUMENT,
            requireSensitive = false,
            requestedRange = Duration.ofDays(7).plusSeconds(1),
        )

        assertNull(access)
    }

    private fun engagementRequest(
        organizationId: UUID,
        auditorUserId: UUID? = null,
        principalGroupId: UUID? = null,
    ): AuditEngagementService.AuditEngagementRequest = AuditEngagementService.AuditEngagementRequest(
        organizationId = organizationId,
        auditorUserId = auditorUserId,
        principalGroupId = principalGroupId,
        categories = setOf(AuditCategory.DOCUMENT),
        sensitivityLevel = AuditEngagementSensitivity.STANDARD,
        startsAt = Instant.now(),
        expiresAt = Instant.now().plusSeconds(3600),
        purpose = "Investigation",
        legalBasis = "Contractual audit right",
    )

    @Test
    fun `requesting an engagement rejects an auditor user outside the engagement organization`()
    {
        val organizationId = UUID.randomUUID()
        val auditorUserId = UUID.randomUUID()
        val requesterId = UUID.randomUUID()
        val repository = mock<AuditEngagementRepository>()
        val appUserService = mock<AppUserService>()
        whenever(appUserService.getById(requesterId)).thenReturn(mock<AppUser>())
        whenever(appUserService.getById(auditorUserId)).thenReturn(mock<AppUser>())
        val organizationMembershipService = mock<OrganizationMembershipService>()
        whenever(organizationMembershipService.isMember(auditorUserId, organizationId)).thenReturn(false)
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            appUserService,
            mock<OrganizationGroupService>(),
            organizationMembershipService,
            mock<PrincipalGroupService>(),
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.requestEngagement(
                request = engagementRequest(organizationId = organizationId, auditorUserId = auditorUserId),
                requestedByUserId = requesterId,
            )
        }

        verify(repository, never()).save(any())
    }

    @Test
    fun `requesting an engagement rejects a principal group owned by a different organization`()
    {
        val organizationId = UUID.randomUUID()
        val foreignOrganizationId = UUID.randomUUID()
        val principalGroupId = UUID.randomUUID()
        val requesterId = UUID.randomUUID()
        val repository = mock<AuditEngagementRepository>()
        val appUserService = mock<AppUserService>()
        whenever(appUserService.getById(requesterId)).thenReturn(mock<AppUser>())
        val organizationGroupService = mock<OrganizationGroupService>()
        whenever(organizationGroupService.getById(principalGroupId.toString())).thenReturn(
            PrincipalGroup().apply { ownerOrganizationId = foreignOrganizationId }
        )
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            appUserService,
            organizationGroupService,
            mock<OrganizationMembershipService>(),
            mock<PrincipalGroupService>(),
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.requestEngagement(
                request = engagementRequest(organizationId = organizationId, principalGroupId = principalGroupId),
                requestedByUserId = requesterId,
            )
        }

        verify(repository, never()).save(any())
    }

    @Test
    fun `approveEngagement rejects an engagement outside the expected organization`()
    {
        val organizationId = UUID.randomUUID()
        val foreignOrganizationId = UUID.randomUUID()
        val engagementId = UUID.randomUUID()
        val approverId = UUID.randomUUID()
        val repository = mock<AuditEngagementRepository>()
        val engagement = activeEngagement().apply {
            id = engagementId
            this.organizationId = foreignOrganizationId
            status = AuditEngagementStatus.REQUESTED
        }
        whenever(repository.findById(engagementId)).thenReturn(engagement)
        val appUserService = mock<AppUserService>()
        whenever(appUserService.getById(approverId)).thenReturn(mock<AppUser>())
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            appUserService,
            mock<OrganizationGroupService>(),
            mock<OrganizationMembershipService>(),
            mock<PrincipalGroupService>(),
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )

        assertThrows(AuditEngagementNotFoundException::class.java) {
            service.approveEngagement(
                engagementId = engagementId,
                expectedOrganizationId = organizationId,
                approvedByUserId = approverId,
            )
        }

        verify(repository, never()).update(any())
    }

    @Test
    fun `revokeEngagement rejects an engagement outside the expected organization`()
    {
        val organizationId = UUID.randomUUID()
        val foreignOrganizationId = UUID.randomUUID()
        val engagementId = UUID.randomUUID()
        val revokerId = UUID.randomUUID()
        val repository = mock<AuditEngagementRepository>()
        val engagement = activeEngagement().apply {
            id = engagementId
            this.organizationId = foreignOrganizationId
        }
        whenever(repository.findById(engagementId)).thenReturn(engagement)
        val appUserService = mock<AppUserService>()
        whenever(appUserService.getById(revokerId)).thenReturn(mock<AppUser>())
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            appUserService,
            mock<OrganizationGroupService>(),
            mock<OrganizationMembershipService>(),
            mock<PrincipalGroupService>(),
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )

        assertThrows(AuditEngagementNotFoundException::class.java) {
            service.revokeEngagement(
                engagementId = engagementId,
                expectedOrganizationId = organizationId,
                revokedByUserId = revokerId,
            )
        }

        verify(repository, never()).update(any())
    }

    @Test
    fun `a platform-scoped engagement cannot be approved through an organization-scoped call`()
    {
        val organizationId = UUID.randomUUID()
        val engagementId = UUID.randomUUID()
        val approverId = UUID.randomUUID()
        val repository = mock<AuditEngagementRepository>()
        val engagement = activeEngagement().apply {
            id = engagementId
            this.organizationId = null
            status = AuditEngagementStatus.REQUESTED
        }
        whenever(repository.findById(engagementId)).thenReturn(engagement)
        val appUserService = mock<AppUserService>()
        whenever(appUserService.getById(approverId)).thenReturn(mock<AppUser>())
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            appUserService,
            mock<OrganizationGroupService>(),
            mock<OrganizationMembershipService>(),
            mock<PrincipalGroupService>(),
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )

        assertThrows(AuditEngagementNotFoundException::class.java) {
            service.approveEngagement(
                engagementId = engagementId,
                expectedOrganizationId = organizationId,
                approvedByUserId = approverId,
            )
        }

        verify(repository, never()).update(any())
    }

    @Test
    fun `listForOrganization delegates to the repository scoped by organization`()
    {
        val organizationId = UUID.randomUUID()
        val repository = mock<AuditEngagementRepository>()
        val expected = listOf(activeEngagement())
        whenever(repository.findByOrganization(organizationId)).thenReturn(expected)
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            mock<AppUserService>(),
            mock<OrganizationGroupService>(),
            mock<OrganizationMembershipService>(),
            mock<PrincipalGroupService>(),
            mock<StepUpAuthService>(),
            mock<AuditRecorder>(),
        )

        val result = service.listForOrganization(organizationId)

        assertEquals(expected, result)
        verify(repository, never()).findByOrganization(null)
    }

    @Test
    fun `expiration locks due engagements before changing status and recording events`()
    {
        val now = Instant.parse("2026-07-13T10:00:00Z")
        val repository = mock<AuditEngagementRepository>()
        val engagement = activeEngagement(expiresAt = now.minusSeconds(1))
        whenever(repository.findDueForExpiryForUpdate(Timestamp.from(now), 100)).thenReturn(listOf(engagement))
        val recorder = mock<AuditRecorder>()
        val service = AuditEngagementService(
            repository,
            mock<OrganizationService>(),
            mock<AppUserService>(),
            mock<OrganizationGroupService>(),
            mock<OrganizationMembershipService>(),
            mock<PrincipalGroupService>(),
            mock<StepUpAuthService>(),
            recorder,
        )

        val expired = service.expireDue(now)

        assertEquals(1, expired)
        assertEquals(AuditEngagementStatus.EXPIRED, engagement.status)
        verify(repository).update(engagement)
        verify(recorder).record(any())
    }
}
