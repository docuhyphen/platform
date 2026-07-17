package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.OrganizationTrustDtoTransformer
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class OrganizationTrustQueryServiceTest
{
    private val relationshipService = mock<OrganizationTrustRelationshipService>()
    private val policyService = mock<OrganizationTrustPolicyService>()
    private val organizationService = mock<OrganizationService>()
    private val authorizationService = mock<AuthorizationService>()
    private val authorizationContextFactory = mock<AuthorizationContextFactory>()
    private val authTokenContext = AuthTokenContext()
    private val currentOrganizationId = UUID.randomUUID()
    private val partnerOrganizationId = UUID.randomUUID()
    private val outsiderOrganizationId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()

    @Test
    fun `list projects only the current party view and partner policy ownership`()
    {
        val service = service()
        val relationship = relationship()
        val currentPolicy = policy(relationship.id, currentOrganizationId)
        val partnerPolicy = policy(relationship.id, partnerOrganizationId)
        whenever(relationshipService.listForParty(currentOrganizationId)).thenReturn(listOf(relationship))
        whenever(relationshipService.activeSuspensions(relationship.id)).thenReturn(emptyList())
        whenever(relationshipService.getById(relationship.id)).thenReturn(relationship)
        whenever(policyService.getPolicies(relationship.id)).thenReturn(listOf(currentPolicy, partnerPolicy))
        whenever(organizationService.getOrganizationById(any())).thenAnswer { organization(it.getArgument(0)) }

        val relationshipDto = service.listRelationships().single()
        val policyDtos = service.getPolicies(relationship.id).policies

        assertEquals(partnerOrganizationId, relationshipDto.partnerOrganizationId)
        assertEquals("Organization $partnerOrganizationId", relationshipDto.partnerOrganizationName)
        assertEquals(currentOrganizationId, policyDtos.single { it.ownedByCurrentOrganization }.policyOwnerOrganizationId)
        assertTrue(policyDtos.single { !it.ownedByCurrentOrganization }.allowExchangesFromPartner)
    }

    @Test
    fun `guessed relationship from an unrelated active organization fails closed`()
    {
        val service = service(outsiderOrganizationId)
        val relationship = relationship()
        whenever(relationshipService.getById(relationship.id)).thenReturn(relationship)

        assertThrows<OrganizationTrustNotFoundException> { service.getRelationship(relationship.id) }
        verify(organizationService, never()).getOrganizationById(any())
    }

    private fun service(activeOrganizationId: UUID = currentOrganizationId): OrganizationTrustQueryService
    {
        authTokenContext.activeOrganizationId = activeOrganizationId
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(PrincipalRef.user(actorId))
        whenever(authorizationContextFactory.currentContext()).thenReturn(
            AuthorizationContext(activeOrgId = activeOrganizationId),
        )
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        return OrganizationTrustQueryService(
            relationshipService,
            policyService,
            organizationService,
            OrganizationTrustDtoTransformer(),
            authorizationService,
            authorizationContextFactory,
            authTokenContext,
        )
    }

    private fun relationship(): OrganizationTrustRelationship = OrganizationTrustRelationship().apply {
        organizationAId = currentOrganizationId
        organizationBId = partnerOrganizationId
        requestedByOrganizationId = currentOrganizationId
        requestedByAppUserId = actorId
        status = OrganizationTrustRelationshipStatus.ACTIVE
        requestedAt = Timestamp.from(Instant.now().minusSeconds(60))
        requestExpiresAt = Timestamp.from(Instant.now().plusSeconds(60))
    }

    private fun organization(id: UUID): Organization = Organization().apply {
        this.id = id
        name = "Organization $id"
        registrationNumber = id.toString()
        isActive = true
        verificationComplete = true
    }

    private fun policy(relationshipId: UUID, ownerId: UUID): OrganizationTrustPartyPolicy =
        OrganizationTrustPartyPolicy().apply {
            this.relationshipId = relationshipId
            policyOwnerOrganizationId = ownerId
            allowExchangesFromPartner = true
        }
}
