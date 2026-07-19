package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class OrganizationTrustExchangePolicyServiceTest
{
    private val relationshipService = mock<OrganizationTrustRelationshipService>()
    private val policyService = mock<OrganizationTrustPolicyService>()
    private val organizationService = mock<OrganizationService>()
    private val service = OrganizationTrustExchangePolicyService(
        relationshipService,
        policyService,
        organizationService,
    )
    private val senderOrganizationId = UUID.randomUUID()
    private val receiverOrganizationId = UUID.randomUUID()
    private val now = Instant.parse("2026-07-18T12:00:00Z")
    private lateinit var relationship: OrganizationTrustRelationship
    private lateinit var senderPolicy: OrganizationTrustPartyPolicy
    private lateinit var receiverPolicy: OrganizationTrustPartyPolicy

    @BeforeEach
    fun configureEligibleRelationship()
    {
        relationship = relationship()
        senderPolicy = policy(senderOrganizationId)
        receiverPolicy = policy(receiverOrganizationId)
        whenever(relationshipService.findCurrentForOrganizations(senderOrganizationId, receiverOrganizationId))
            .thenReturn(relationship)
        whenever(relationshipService.isEffectivelySuspended(relationship.id)).thenReturn(false)
        whenever(organizationService.getOrganizationById(senderOrganizationId))
            .thenReturn(organization(senderOrganizationId))
        whenever(organizationService.getOrganizationById(receiverOrganizationId))
            .thenReturn(organization(receiverOrganizationId))
        whenever(policyService.getPolicy(relationship.id, senderOrganizationId)).thenReturn(senderPolicy)
        whenever(policyService.getPolicy(relationship.id, receiverOrganizationId)).thenReturn(receiverPolicy)
    }

    @ParameterizedTest
    @CsvSource(
        "true,true,true",
        "true,false,false",
        "false,true,false",
        "false,false,false",
    )
    fun `combines sender outbound and receiver inbound policy`(
        senderAllows: Boolean,
        receiverAllows: Boolean,
        expected: Boolean,
    )
    {
        senderPolicy.allowExchangesToPartner = senderAllows
        receiverPolicy.allowExchangesFromPartner = receiverAllows

        assertTrue(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now) == expected)
    }

    @Test
    fun `rejects expired sender or receiver policies`()
    {
        senderPolicy.expiresAt = Timestamp.from(now)
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))

        senderPolicy.expiresAt = null
        receiverPolicy.expiresAt = Timestamp.from(now.minusSeconds(1))
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))
    }

    @Test
    fun `rejects overdue policy reviews`()
    {
        senderPolicy.reviewDueAt = Timestamp.from(now)
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))

        senderPolicy.reviewDueAt = null
        receiverPolicy.reviewDueAt = Timestamp.from(now.minusSeconds(1))
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))
    }

    @Test
    fun `rejects an overdue or missing relationship review`()
    {
        relationship.reviewDueAt = Timestamp.from(now)
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))

        relationship.reviewDueAt = null
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))
    }

    @Test
    fun `rejects suspension and terminal relationship state`()
    {
        whenever(relationshipService.isEffectivelySuspended(relationship.id)).thenReturn(true)
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))

        whenever(relationshipService.isEffectivelySuspended(relationship.id)).thenReturn(false)
        relationship.status = OrganizationTrustRelationshipStatus.ENDED
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))
    }

    @Test
    fun `rejects inactive or unverified sender and receiver organizations`()
    {
        whenever(organizationService.getOrganizationById(senderOrganizationId))
            .thenReturn(organization(senderOrganizationId, active = false))
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))

        whenever(organizationService.getOrganizationById(senderOrganizationId))
            .thenReturn(organization(senderOrganizationId))
        whenever(organizationService.getOrganizationById(receiverOrganizationId))
            .thenReturn(organization(receiverOrganizationId, verified = false))
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))
    }

    @Test
    fun `fails closed for missing organizations or policies`()
    {
        whenever(organizationService.getOrganizationById(receiverOrganizationId))
            .thenThrow(OrganizationNotFoundException("missing"))
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))
    }

    @Test
    fun `fails closed for missing directional policy`()
    {
        whenever(policyService.getPolicy(relationship.id, receiverOrganizationId))
            .thenThrow(OrganizationTrustNotFoundException())
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))
    }

    @Test
    fun `rejects absent relationship and allows the same organization`()
    {
        whenever(relationshipService.findCurrentForOrganizations(senderOrganizationId, receiverOrganizationId))
            .thenReturn(null)
        assertFalse(service.permitsExchange(senderOrganizationId, receiverOrganizationId, now))
        assertTrue(service.permitsExchange(senderOrganizationId, senderOrganizationId, now))
    }

    private fun relationship(): OrganizationTrustRelationship = OrganizationTrustRelationship().apply {
        organizationAId = senderOrganizationId
        organizationBId = receiverOrganizationId
        requestedByOrganizationId = senderOrganizationId
        requestedByAppUserId = UUID.randomUUID()
        status = OrganizationTrustRelationshipStatus.ACTIVE
        activatedAt = Timestamp.from(now.minusSeconds(60))
        reviewDueAt = Timestamp.from(now.plusSeconds(3600))
    }

    private fun policy(ownerOrganizationId: UUID): OrganizationTrustPartyPolicy =
        OrganizationTrustPartyPolicy().apply {
            relationshipId = relationship.id
            policyOwnerOrganizationId = ownerOrganizationId
            allowExchangesToPartner = true
            allowExchangesFromPartner = true
            expiresAt = Timestamp.from(now.plusSeconds(3600))
            reviewDueAt = Timestamp.from(now.plusSeconds(1800))
        }

    private fun organization(
        id: UUID,
        active: Boolean = true,
        verified: Boolean = true,
    ): Organization = Organization().apply {
        this.id = id
        name = "Organization $id"
        registrationNumber = id.toString()
        isActive = active
        verificationComplete = verified
    }
}
