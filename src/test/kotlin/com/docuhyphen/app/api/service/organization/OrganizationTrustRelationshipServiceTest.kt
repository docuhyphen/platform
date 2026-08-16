package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustAuthorizationException
import com.docuhyphen.app.api.exception.OrganizationTrustConflictException
import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.exception.OrganizationTrustStaleVersionException
import com.docuhyphen.app.api.exception.OrganizationTrustValidationException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationTrustDecision
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import com.docuhyphen.app.api.model.entity.OrganizationTrustSuspension
import com.docuhyphen.app.api.repository.organization.OrganizationTrustRelationshipRepository
import com.docuhyphen.app.api.repository.organization.OrganizationTrustSuspensionRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.TrustedGroupAccessReconciliationService
import jakarta.inject.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class OrganizationTrustRelationshipServiceTest
{
    private val relationshipRepository = mock<OrganizationTrustRelationshipRepository>()
    private val suspensionRepository = mock<OrganizationTrustSuspensionRepository>()
    private val organizationService = mock<OrganizationService>()
    private val policyService = mock<OrganizationTrustPolicyService>()
    private val authorizationService = mock<AuthorizationService>()
    private val authorizationContextFactory = mock<AuthorizationContextFactory>()
    private val authTokenContext = AuthTokenContext()
    private val auditRecorder = mock<AuditRecorder>()
    private val trustedGroupAccessReconciliationService = mock<TrustedGroupAccessReconciliationService>()
    private val trustedGroupAccessReconciliationProvider = mock<Provider<TrustedGroupAccessReconciliationService>>()
    private val trustConfig = OrganizationTrustConfigService(14, 7, 365, 10, 24)

    private val organizationAId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val organizationBId = UUID.fromString("20000000-0000-0000-0000-000000000002")
    private val outsiderOrganizationId = UUID.fromString("30000000-0000-0000-0000-000000000003")
    private val actorId = UUID.fromString("40000000-0000-0000-0000-000000000004")
    private val now = Instant.parse("2026-07-16T08:00:00Z")

    @Test
    fun `request creates a canonical generation with two default policies and correlated audits`()
    {
        val service = serviceFor(organizationBId)
        whenever(relationshipRepository.findCurrentForOrganizations(organizationAId, organizationBId)).thenReturn(null)
        whenever(relationshipRepository.findLatestTerminalForOrganizations(organizationAId, organizationBId)).thenReturn(null)
        whenever(relationshipRepository.insertAndFlush(any())).thenAnswer { it.getArgument(0) }
        whenever(policyService.initializeDefaultPolicies(any(), eq(now))).thenReturn(emptyList())

        val outcome = service.requestRelationship(
            organizationAId,
            "  Collaborate securely  ",
            now,
        )
        val relationship = outcome.relationship

        assertEquals(organizationAId, relationship.organizationAId)
        assertEquals(organizationBId, relationship.organizationBId)
        assertEquals(organizationBId, relationship.requestedByOrganizationId)
        assertEquals("Collaborate securely", relationship.requestMessage)
        assertEquals(AuditEventType.ORG_TRUST_REQUESTED, outcome.eventType)
        assertEquals(now.plusSeconds(14 * 24 * 60 * 60L), relationship.requestExpiresAt.toInstant())
        verify(relationshipRepository).lockOrganizations(organizationAId, organizationBId)
        verify(policyService).initializeDefaultPolicies(relationship, now)

        val drafts = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder, times(2)).record(drafts.capture())
        assertEquals(setOf(organizationAId, organizationBId), drafts.allValues.map { ownerId(it) }.toSet())
        assertTrue(drafts.allValues.all { it.eventTypeKey == AuditEventType.ORG_TRUST_REQUESTED.key })
        assertEquals(1, drafts.allValues.map { it.businessTransactionId }.distinct().size)
    }

    @Test
    fun `request rejects self trust and ineligible organizations before persistence`()
    {
        val service = serviceFor(organizationAId)

        assertThrows<OrganizationTrustValidationException> {
            service.requestRelationship(organizationAId, null, now)
        }
        verify(relationshipRepository, never()).insertAndFlush(any())

        whenever(organizationService.getOrganizationById(organizationBId)).thenReturn(
            organization(organizationBId, active = true, verified = false),
        )
        assertThrows<OrganizationTrustValidationException> {
            service.requestRelationship(organizationBId, null, now)
        }
    }

    @Test
    fun `request expires a stale pending generation and enforces cooldown`()
    {
        val service = serviceFor(organizationAId)
        val expiredPending = relationship(
            status = OrganizationTrustRelationshipStatus.PENDING,
            requester = organizationAId,
        ).apply {
            requestExpiresAt = Timestamp.from(now)
        }
        whenever(relationshipRepository.findCurrentForOrganizations(organizationAId, organizationBId)).thenReturn(expiredPending)
        whenever(relationshipRepository.updateAndFlush(any())).thenAnswer { it.getArgument(0) }

        assertThrows<OrganizationTrustConflictException> {
            service.requestRelationship(organizationBId, null, now)
        }

        assertEquals(OrganizationTrustRelationshipStatus.EXPIRED, expiredPending.status)
        assertEquals(now, expiredPending.expiredAt?.toInstant())
        verify(auditRecorder, times(2)).record(any())
        verify(relationshipRepository, never()).insertAndFlush(any())
    }

    @Test
    fun `requested organization can accept a current request with the expected version`()
    {
        val service = serviceFor(organizationBId)
        val relationship = relationship(OrganizationTrustRelationshipStatus.PENDING, organizationAId).apply {
            version = 4
            requestExpiresAt = Timestamp.from(now.plusSeconds(60))
        }
        whenever(relationshipRepository.findById(relationship.id)).thenReturn(relationship)
        whenever(relationshipRepository.updateAndFlush(any())).thenAnswer { it.getArgument(0) }

        val accepted = service.decide(
            relationship.id,
            OrganizationTrustDecision.ACCEPT,
            "  Approved  ",
            4,
            now,
        )

        assertEquals(OrganizationTrustRelationshipStatus.ACTIVE, accepted.status)
        assertEquals(now, accepted.activatedAt?.toInstant())
        assertEquals(now.plusSeconds(365 * 24 * 60 * 60L), accepted.reviewDueAt?.toInstant())
        assertEquals("Approved", accepted.latestTransitionReason)
        verify(auditRecorder, times(2)).record(any())
    }

    @Test
    fun `terminal transitions enforce the controlling party and preserve their reason`()
    {
        whenever(relationshipRepository.updateAndFlush(any())).thenAnswer { it.getArgument(0) }

        val rejected = relationship(OrganizationTrustRelationshipStatus.PENDING, organizationAId).apply {
            requestExpiresAt = Timestamp.from(now.plusSeconds(60))
        }
        whenever(relationshipRepository.findById(rejected.id)).thenReturn(rejected)
        serviceFor(organizationBId).decide(
            rejected.id,
            OrganizationTrustDecision.REJECT,
            "Not ready",
            0,
            now,
        )
        assertEquals(OrganizationTrustRelationshipStatus.REJECTED, rejected.status)
        assertEquals("Not ready", rejected.latestTransitionReason)

        val withdrawn = relationship(OrganizationTrustRelationshipStatus.PENDING, organizationAId).apply {
            requestExpiresAt = Timestamp.from(now.plusSeconds(60))
        }
        whenever(relationshipRepository.findById(withdrawn.id)).thenReturn(withdrawn)
        serviceFor(organizationAId).withdraw(withdrawn.id, "Sent in error", 0, now)
        assertEquals(OrganizationTrustRelationshipStatus.WITHDRAWN, withdrawn.status)

        val ended = relationship(OrganizationTrustRelationshipStatus.ACTIVE, organizationAId)
        whenever(relationshipRepository.findById(ended.id)).thenReturn(ended)
        serviceFor(organizationBId).terminate(ended.id, "Contract ended", 0, now)
        assertEquals(OrganizationTrustRelationshipStatus.ENDED, ended.status)
        assertEquals(now, ended.endedAt?.toInstant())
        verify(auditRecorder, times(6)).record(any())
    }

    @Test
    fun `a new generation after cooldown is identified and audited as a re-request`()
    {
        val service = serviceFor(organizationAId)
        val previous = relationship(OrganizationTrustRelationshipStatus.REJECTED, organizationBId).apply {
            requestedAt = Timestamp.from(now.minusSeconds(10 * 24 * 60 * 60L))
            rejectedAt = Timestamp.from(now.minusSeconds(8 * 24 * 60 * 60L))
            requestExpiresAt = Timestamp.from(now.minusSeconds(9 * 24 * 60 * 60L))
        }
        whenever(relationshipRepository.findCurrentForOrganizations(organizationAId, organizationBId)).thenReturn(null)
        whenever(relationshipRepository.findLatestTerminalForOrganizations(organizationAId, organizationBId)).thenReturn(previous)
        whenever(relationshipRepository.insertAndFlush(any())).thenAnswer { it.getArgument(0) }
        whenever(policyService.initializeDefaultPolicies(any(), eq(now))).thenReturn(emptyList())

        val outcome = service.requestRelationship(organizationBId, null, now)

        assertEquals(AuditEventType.ORG_TRUST_REREQUESTED, outcome.eventType)
        val drafts = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder, times(2)).record(drafts.capture())
        assertTrue(drafts.allValues.all { it.eventTypeKey == AuditEventType.ORG_TRUST_REREQUESTED.key })
    }

    @Test
    fun `requester unrelated organization and stale version cannot decide`()
    {
        val relationship = relationship(OrganizationTrustRelationshipStatus.PENDING, organizationAId).apply {
            version = 2
            requestExpiresAt = Timestamp.from(now.plusSeconds(60))
        }

        val requesterService = serviceFor(organizationAId)
        whenever(relationshipRepository.findById(relationship.id)).thenReturn(relationship)
        assertThrows<OrganizationTrustAuthorizationException> {
            requesterService.decide(relationship.id, OrganizationTrustDecision.ACCEPT, null, 2, now)
        }

        val outsiderService = serviceFor(outsiderOrganizationId)
        assertThrows<OrganizationTrustNotFoundException> {
            outsiderService.decide(relationship.id, OrganizationTrustDecision.ACCEPT, null, 2, now)
        }

        val recipientService = serviceFor(organizationBId)
        assertThrows<OrganizationTrustStaleVersionException> {
            recipientService.decide(relationship.id, OrganizationTrustDecision.ACCEPT, null, 1, now)
        }
    }

    @Test
    fun `each party independently suspends and can clear only its own suspension`()
    {
        val service = serviceFor(organizationBId)
        val relationship = relationship(OrganizationTrustRelationshipStatus.ACTIVE, organizationAId)
        whenever(relationshipRepository.findById(relationship.id)).thenReturn(relationship)
        whenever(suspensionRepository.findActiveByOwner(relationship.id, organizationBId)).thenReturn(null)
        whenever(suspensionRepository.insertAndFlush(any())).thenAnswer { it.getArgument(0) }

        val suspension = service.suspend(relationship.id, "  Security review  ", now)
        assertEquals(organizationBId, suspension.suspendingOrganizationId)
        assertEquals("Security review", suspension.reason)

        whenever(suspensionRepository.findById(suspension.id)).thenReturn(suspension)
        whenever(suspensionRepository.updateAndFlush(any())).thenAnswer { it.getArgument(0) }
        val cleared = service.resume(relationship.id, suspension.id, now.plusSeconds(60))
        assertFalse(cleared.isActive())
        assertEquals(actorId, cleared.clearedByAppUserId)
        verify(trustedGroupAccessReconciliationService).reconcileRelationship(relationship.id)
        verify(auditRecorder, times(4)).record(any())
    }

    @Test
    fun `resume waits for every suspension to clear before reconciling trusted group access`()
    {
        val service = serviceFor(organizationBId)
        val relationship = relationship(OrganizationTrustRelationshipStatus.ACTIVE, organizationAId)
        val ownSuspension = OrganizationTrustSuspension().apply {
            relationshipId = relationship.id
            suspendingOrganizationId = organizationBId
            reason = "Security review"
            suspendedByAppUserId = actorId
            suspendedAt = Timestamp.from(now.minusSeconds(60))
        }
        val partnerSuspension = OrganizationTrustSuspension().apply {
            relationshipId = relationship.id
            suspendingOrganizationId = organizationAId
            reason = "Partner review"
            suspendedByAppUserId = UUID.randomUUID()
            suspendedAt = Timestamp.from(now.minusSeconds(30))
        }
        whenever(relationshipRepository.findById(relationship.id)).thenReturn(relationship)
        whenever(suspensionRepository.findById(ownSuspension.id)).thenReturn(ownSuspension)
        whenever(suspensionRepository.updateAndFlush(any())).thenAnswer { it.getArgument(0) }
        whenever(suspensionRepository.findActive(relationship.id)).thenReturn(listOf(partnerSuspension))

        service.resume(relationship.id, ownSuspension.id, now)

        verify(trustedGroupAccessReconciliationService, never()).reconcileRelationship(any())
    }

    @Test
    fun `expiry scheduler transition is idempotent because only pending due rows are returned`()
    {
        val service = serviceFor(organizationAId)
        val first = relationship(OrganizationTrustRelationshipStatus.PENDING, organizationAId)
        val second = relationship(OrganizationTrustRelationshipStatus.PENDING, organizationBId)
        whenever(relationshipRepository.findDueForExpiryForUpdate(Timestamp.from(now), 100))
            .thenReturn(listOf(first, second), emptyList())
        whenever(relationshipRepository.updateAndFlush(any())).thenAnswer { it.getArgument(0) }

        assertEquals(2, service.expireDueRequests(now))
        assertEquals(0, service.expireDueRequests(now))
        assertEquals(OrganizationTrustRelationshipStatus.EXPIRED, first.status)
        assertNull(first.latestTransitionByAppUserId)
        verify(auditRecorder, times(4)).record(any())
    }

    private fun serviceFor(activeOrganizationId: UUID): OrganizationTrustRelationshipService
    {
        authTokenContext.activeOrganizationId = activeOrganizationId
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(PrincipalRef.user(actorId))
        whenever(authorizationContextFactory.currentContext()).thenReturn(
            AuthorizationContext(activeOrgId = activeOrganizationId),
        )
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        whenever(organizationService.getOrganizationById(any())).thenAnswer {
            organization(it.getArgument(0), active = true, verified = true)
        }
        whenever(auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()),
        )
        whenever(trustedGroupAccessReconciliationProvider.get())
            .thenReturn(trustedGroupAccessReconciliationService)
        return OrganizationTrustRelationshipService(
            relationshipRepository,
            suspensionRepository,
            organizationService,
            policyService,
            authorizationService,
            authorizationContextFactory,
            authTokenContext,
            trustConfig,
            auditRecorder,
            trustedGroupAccessReconciliationProvider,
            mock(),
        )
    }

    private fun relationship(
        status: OrganizationTrustRelationshipStatus,
        requester: UUID,
    ): OrganizationTrustRelationship = OrganizationTrustRelationship().apply {
        organizationAId = this@OrganizationTrustRelationshipServiceTest.organizationAId
        organizationBId = this@OrganizationTrustRelationshipServiceTest.organizationBId
        requestedByOrganizationId = requester
        requestedByAppUserId = actorId
        this.status = status
        requestedAt = Timestamp.from(now.minusSeconds(60))
        requestExpiresAt = Timestamp.from(now.plusSeconds(60))
        if (status == OrganizationTrustRelationshipStatus.ACTIVE)
        {
            activatedAt = Timestamp.from(now.minusSeconds(30))
            reviewDueAt = Timestamp.from(now.plusSeconds(3600))
        }
    }

    private fun organization(id: UUID, active: Boolean, verified: Boolean): Organization = Organization().apply {
        this.id = id
        isActive = active
        verificationComplete = verified
        name = "Organization $id"
        registrationNumber = id.toString()
    }

    private fun ownerId(draft: AuditEventDraft): UUID =
        (draft.owner as AuditOwnerScope.Organization).organizationId
}
