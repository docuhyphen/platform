package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.OrganizationTrustDtoTransformer
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.auth.DirectoryLookupGuardService
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class OrganizationDirectorySearchServiceTest
{
    @Test
    fun `repository discovery query enforces organization eligibility and result cap`()
    {
        val activeOrganizationId = UUID.randomUUID()
        val entityManager = mock<EntityManager>()
        val query = mock<TypedQuery<Organization>>()
        val jpql = argumentCaptor<String>()
        whenever(entityManager.createQuery(jpql.capture(), eq(Organization::class.java))).thenReturn(query)
        whenever(query.setParameter("activeOrganizationId", activeOrganizationId)).thenReturn(query)
        whenever(query.setParameter("nameQuery", "%acme%")).thenReturn(query)
        whenever(query.setParameter("exactQuery", "acme")).thenReturn(query)
        whenever(query.resultList).thenReturn(emptyList())
        val repository = OrganizationRepository().apply { this.entityManager = entityManager }

        repository.searchDiscoverableForTrustRequests(activeOrganizationId, "acme", 500)

        assertTrue(jpql.firstValue.contains("o.id <> :activeOrganizationId"))
        assertTrue(jpql.firstValue.contains("o.isActive = true"))
        assertTrue(jpql.firstValue.contains("o.verificationComplete = true"))
        assertTrue(jpql.firstValue.contains("o.settings.discoverableForTrustRequests = true"))
        assertTrue(jpql.firstValue.contains("LOWER(o.registrationNumber) = :exactQuery"))
        assertFalse(jpql.firstValue.contains("allowShareWithoutPairing"))
        verify(query).maxResults = 100
    }

    @Test
    fun `search excludes self and every current relationship without using sharing policy`()
    {
        val activeOrganizationId = UUID.randomUUID()
        val relatedOrganizationId = UUID.randomUUID()
        val visibleOrganizationId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val organizationService = mock<OrganizationService>()
        val relationshipService = mock<OrganizationTrustRelationshipService>()
        val guard = mock<DirectoryLookupGuardService>()
        val configurationService = mock<ConfigurationService>()
        val authorizationService = mock<AuthorizationService>()
        val contextFactory = mock<AuthorizationContextFactory>()
        val tokenContext = AuthTokenContext().apply { this.activeOrganizationId = activeOrganizationId }
        val relationship = OrganizationTrustRelationship().apply {
            organizationAId = activeOrganizationId
            organizationBId = relatedOrganizationId
            requestedByOrganizationId = activeOrganizationId
            requestedByAppUserId = actorId
            status = OrganizationTrustRelationshipStatus.PENDING
        }
        whenever(contextFactory.currentPrincipal()).thenReturn(PrincipalRef.user(actorId))
        whenever(contextFactory.currentContext()).thenReturn(AuthorizationContext(activeOrgId = activeOrganizationId))
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        whenever(configurationService.getDirectoryLookupMaxResults()).thenReturn(20)
        whenever(relationshipService.listForParty(activeOrganizationId)).thenReturn(listOf(relationship))
        whenever(
            organizationService.searchDiscoverableForTrustRequests(activeOrganizationId, "acme", 20),
        ).thenReturn(listOf(organization(relatedOrganizationId), organization(visibleOrganizationId)))
        val service = OrganizationDirectorySearchService(
            organizationService,
            relationshipService,
            OrganizationTrustDtoTransformer(),
            guard,
            configurationService,
            authorizationService,
            contextFactory,
            tokenContext,
        )

        val results = service.search("  ACME  ", "request-id")

        assertEquals(listOf(visibleOrganizationId), results.map { it.id })
        verify(guard).enforce(
            eq("organization-trust-discovery"),
            eq(activeOrganizationId.toString()),
            eq("request-id"),
            eq("acme"),
        )
    }

    private fun organization(id: UUID): Organization = Organization().apply {
        this.id = id
        name = "Organization $id"
        registrationNumber = id.toString()
        isActive = true
        verificationComplete = true
    }
}
