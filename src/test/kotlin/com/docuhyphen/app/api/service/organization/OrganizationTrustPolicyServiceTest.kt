package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustAuthorizationException
import com.docuhyphen.app.api.exception.OrganizationTrustConflictException
import com.docuhyphen.app.api.exception.OrganizationTrustStaleVersionException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import com.docuhyphen.app.api.repository.OrganizationTrustPartyPolicyRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.inject.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class OrganizationTrustPolicyServiceTest
{
    private val policyRepository = mock<OrganizationTrustPartyPolicyRepository>()
    private val authorizationService = mock<AuthorizationService>()
    private val authorizationContextFactory = mock<AuthorizationContextFactory>()
    private val authTokenContext = AuthTokenContext()
    private val auditRecorder = mock<AuditRecorder>()
    private val relationshipService = mock<OrganizationTrustRelationshipService>()
    private val relationshipServiceProvider = mock<Provider<OrganizationTrustRelationshipService>>()
    private val organizationAId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val organizationBId = UUID.fromString("20000000-0000-0000-0000-000000000002")
    private val actorId = UUID.fromString("40000000-0000-0000-0000-000000000004")
    private val now = Instant.parse("2026-07-16T08:00:00Z")

    @Test
    fun `initialization creates exactly one least privilege policy for each party`()
    {
        val service = serviceFor(organizationAId)
        val relationship = relationship()
        whenever(policyRepository.findByRelationship(relationship.id)).thenReturn(emptyList())
        whenever(policyRepository.insertAllAndFlush(any())).thenAnswer { it.getArgument<Collection<OrganizationTrustPartyPolicy>>(0).toList() }

        val policies = service.initializeDefaultPolicies(relationship, now)

        assertEquals(setOf(organizationAId, organizationBId), policies.map { it.policyOwnerOrganizationId }.toSet())
        assertTrue(policies.all { !it.allowExchangesToPartner })
        assertTrue(policies.all { !it.allowExchangesFromPartner })
        assertTrue(policies.all { !it.allowPartnerMemberResolution })
        assertTrue(policies.all { !it.allowPartnerGroupDiscovery })
        assertTrue(policies.all { !it.shareMemberDisplayName })
        assertTrue(policies.all { it.updatedByAppUserId == null })
    }

    @Test
    fun `policy owner can update typed fields and both organizations receive a correlated audit`()
    {
        val service = serviceFor(organizationAId)
        val relationship = relationship()
        val policy = policy(relationship.id, organizationAId).apply { revision = 5 }
        whenever(relationshipService.getById(relationship.id)).thenReturn(relationship)
        whenever(policyRepository.findById(policy.id)).thenReturn(policy)
        whenever(policyRepository.updateAndFlush(any())).thenAnswer {
            it.getArgument<OrganizationTrustPartyPolicy>(0).also { updated -> updated.revision++ }
        }
        val update = OrganizationTrustPolicyUpdate(
            allowExchangesToPartner = true,
            allowExchangesFromPartner = true,
            allowPartnerMemberResolution = true,
            allowPartnerGroupDiscovery = false,
            shareMemberDisplayName = true,
            expiresAt = now.plusSeconds(3600),
            reviewDueAt = now.plusSeconds(1800),
        )

        val saved = service.updatePolicy(relationship.id, policy.id, 5, update, now)

        assertEquals(6, saved.revision)
        assertTrue(saved.allowExchangesToPartner)
        assertTrue(saved.allowExchangesFromPartner)
        assertTrue(saved.allowPartnerMemberResolution)
        assertFalse(saved.allowPartnerGroupDiscovery)
        assertTrue(saved.shareMemberDisplayName)
        assertEquals(actorId, saved.updatedByAppUserId)

        val drafts = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder, times(2)).record(drafts.capture())
        assertEquals(setOf(organizationAId, organizationBId), drafts.allValues.map { ownerId(it) }.toSet())
        assertEquals(1, drafts.allValues.map { it.businessTransactionId }.distinct().size)
        assertTrue(drafts.allValues.all { "allowExchangesToPartner" in it.payload.getValue("changedFields") })
        assertTrue(drafts.allValues.all { it.payload.values.none { value -> value == "true" || value == "false" } })
    }

    @Test
    fun `partner cannot edit another organization's policy and stale revisions fail`()
    {
        val relationship = relationship()
        val policy = policy(relationship.id, organizationAId).apply { revision = 2 }
        whenever(policyRepository.findById(policy.id)).thenReturn(policy)
        val update = OrganizationTrustPolicyUpdate(false, false, false, false, false, null, null)

        val partnerService = serviceFor(organizationBId)
        whenever(relationshipService.getById(relationship.id)).thenReturn(relationship)
        assertThrows<OrganizationTrustAuthorizationException> {
            partnerService.updatePolicy(relationship.id, policy.id, 2, update, now)
        }

        val ownerService = serviceFor(organizationAId)
        whenever(relationshipService.getById(relationship.id)).thenReturn(relationship)
        assertThrows<OrganizationTrustStaleVersionException> {
            ownerService.updatePolicy(relationship.id, policy.id, 1, update, now)
        }
    }

    @Test
    fun `terminal relationship policy is immutable`()
    {
        val service = serviceFor(organizationAId)
        val relationship = relationship().apply {
            status = OrganizationTrustRelationshipStatus.ENDED
            endedAt = Timestamp.from(now)
        }
        whenever(relationshipService.getById(relationship.id)).thenReturn(relationship)

        assertThrows<OrganizationTrustConflictException> {
            service.updatePolicy(
                relationship.id,
                UUID.randomUUID(),
                0,
                OrganizationTrustPolicyUpdate(false, false, false, false, false, null, null),
                now,
            )
        }
    }

    private fun serviceFor(activeOrganizationId: UUID): OrganizationTrustPolicyService
    {
        authTokenContext.activeOrganizationId = activeOrganizationId
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(PrincipalRef.user(actorId))
        whenever(authorizationContextFactory.currentContext()).thenReturn(
            AuthorizationContext(activeOrgId = activeOrganizationId),
        )
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        whenever(auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()),
        )
        whenever(relationshipServiceProvider.get()).thenReturn(relationshipService)
        whenever(relationshipService.getById(any())).thenAnswer {
            val relationshipId = it.getArgument<UUID>(0)
            relationship().apply { id = relationshipId }
        }
        return OrganizationTrustPolicyService(
            policyRepository,
            authorizationService,
            authorizationContextFactory,
            authTokenContext,
            auditRecorder,
            relationshipServiceProvider,
        )
    }

    private fun relationship(): OrganizationTrustRelationship = OrganizationTrustRelationship().apply {
        organizationAId = this@OrganizationTrustPolicyServiceTest.organizationAId
        organizationBId = this@OrganizationTrustPolicyServiceTest.organizationBId
        requestedByOrganizationId = organizationAId
        requestedByAppUserId = actorId
        status = OrganizationTrustRelationshipStatus.ACTIVE
        requestedAt = Timestamp.from(now.minusSeconds(120))
        requestExpiresAt = Timestamp.from(now.minusSeconds(60))
        activatedAt = Timestamp.from(now.minusSeconds(30))
        reviewDueAt = Timestamp.from(now.plusSeconds(3600))
    }

    private fun policy(relationshipId: UUID, ownerOrganizationId: UUID): OrganizationTrustPartyPolicy =
        OrganizationTrustPartyPolicy().apply {
            this.relationshipId = relationshipId
            policyOwnerOrganizationId = ownerOrganizationId
            updatedAt = Timestamp.from(now.minusSeconds(60))
        }

    private fun ownerId(draft: AuditEventDraft): UUID =
        (draft.owner as AuditOwnerScope.Organization).organizationId
}
