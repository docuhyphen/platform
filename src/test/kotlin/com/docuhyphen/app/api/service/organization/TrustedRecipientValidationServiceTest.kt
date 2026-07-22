package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestationSubjectType
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class TrustedRecipientValidationServiceTest
{
    private val relationshipService = mock<OrganizationTrustRelationshipService>()
    private val policyService = mock<OrganizationTrustPolicyService>()
    private val organizationService = mock<OrganizationService>()
    private val groupService = mock<OrganizationGroupService>()
    private val membershipService = mock<OrganizationMembershipService>()
    private val appUserService = mock<com.docuhyphen.app.api.service.AppUserService>()
    private val service = TrustedRecipientValidationService(
        relationshipService,
        policyService,
        organizationService,
        groupService,
        membershipService,
        appUserService,
    )
    private val callerOrganizationId = UUID.randomUUID()
    private val targetOrganizationId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val now = Instant.parse("2026-07-16T10:00:00Z")

    @Test
    fun `active trust and compatible directional policy allow only the published target group`()
    {
        configureEligibleTrust()
        val group = publishedGroup()
        whenever(groupService.getPublishedExchangeGroup(targetOrganizationId, groupId)).thenReturn(group)

        val validation = service.validateGroupSelection(callerOrganizationId, targetOrganizationId, groupId, now)

        assertEquals(groupId, validation.group.id)
        assertEquals(targetOrganizationId, validation.targetOrganization.id)
        assertEquals(now.plusSeconds(3600), validation.verificationExpiresAt)
    }

    @Test
    fun `effective suspension blocks group discovery and selection`()
    {
        val relationship = relationship()
        whenever(relationshipService.findCurrentForOrganizations(callerOrganizationId, targetOrganizationId))
            .thenReturn(relationship)
        whenever(relationshipService.isEffectivelySuspended(relationship.id)).thenReturn(true)

        assertThrows<OrganizationTrustNotFoundException> {
            service.validateGroupDiscovery(callerOrganizationId, targetOrganizationId, now)
        }
    }

    @Test
    fun `group substitution fails when the group is not a published target-owned group`()
    {
        configureEligibleTrust()
        whenever(groupService.getPublishedExchangeGroup(targetOrganizationId, groupId)).thenReturn(null)

        assertThrows<OrganizationTrustNotFoundException> {
            service.validateGroupSelection(callerOrganizationId, targetOrganizationId, groupId, now)
        }
    }

    @Test
    fun `sender or receiver policy denial fails closed`()
    {
        configureEligibleTrust(senderAllows = false)

        assertThrows<OrganizationTrustNotFoundException> {
            service.validateGroupDiscovery(callerOrganizationId, targetOrganizationId, now)
        }
    }

    @Test
    fun `overdue sender policy review blocks discovery resolution and acceptance`()
    {
        configureEligibleTrust(senderReviewDueAt = now.minusSeconds(1))

        assertThrows<OrganizationTrustNotFoundException> {
            service.validateGroupDiscovery(callerOrganizationId, targetOrganizationId, now)
        }
        assertThrows<OrganizationTrustNotFoundException> {
            service.validatePersonResolution(callerOrganizationId, targetOrganizationId, now)
        }

        val subjectAppUserId = UUID.randomUUID()
        val membershipId = UUID.randomUUID()
        configureEligiblePersonAcceptance(senderReviewDueAt = now.minusSeconds(1))
        assertThrows<OrganizationTrustNotFoundException> {
            service.validatePersonAttestation(
                personAttestation(relationship().id, subjectAppUserId, membershipId, "member@partner.example"),
                now,
            )
        }
    }

    @Test
    fun `overdue target policy review blocks discovery resolution and acceptance`()
    {
        configureEligibleTrust(targetReviewDueAt = now)

        assertThrows<OrganizationTrustNotFoundException> {
            service.validateGroupDiscovery(callerOrganizationId, targetOrganizationId, now)
        }
        assertThrows<OrganizationTrustNotFoundException> {
            service.validatePersonResolution(callerOrganizationId, targetOrganizationId, now)
        }

        val subjectAppUserId = UUID.randomUUID()
        val membershipId = UUID.randomUUID()
        configureEligiblePersonAcceptance(targetReviewDueAt = now)
        assertThrows<OrganizationTrustNotFoundException> {
            service.validatePersonAttestation(
                personAttestation(relationship().id, subjectAppUserId, membershipId, "member@partner.example"),
                now,
            )
        }
    }

    @Test
    fun `earliest future policy review caps verification expiry`()
    {
        configureEligibleTrust(
            senderReviewDueAt = now.plusSeconds(120),
            targetReviewDueAt = now.plusSeconds(300),
        )

        val validation = service.validatePersonResolution(callerOrganizationId, targetOrganizationId, now)

        assertEquals(now.plusSeconds(120), validation.verificationExpiresAt)
    }

    @Test
    fun `missing optional policy dates remain eligible`()
    {
        configureEligibleTrust()

        val validation = service.validatePersonResolution(callerOrganizationId, targetOrganizationId, now)

        assertEquals(now.plusSeconds(3600), validation.verificationExpiresAt)
    }

    @Test
    fun `member resolution requires the target member resolution policy independently from group discovery`()
    {
        val relationship = relationship()
        whenever(relationshipService.findCurrentForOrganizations(callerOrganizationId, targetOrganizationId))
            .thenReturn(relationship)
        whenever(relationshipService.isEffectivelySuspended(relationship.id)).thenReturn(false)
        whenever(organizationService.getOrganizationById(callerOrganizationId))
            .thenReturn(organization(callerOrganizationId))
        whenever(organizationService.getOrganizationById(targetOrganizationId))
            .thenReturn(organization(targetOrganizationId))
        whenever(policyService.getPolicy(relationship.id, callerOrganizationId))
            .thenReturn(policy(relationship.id, callerOrganizationId, toPartner = true))
        whenever(policyService.getPolicy(relationship.id, targetOrganizationId))
            .thenReturn(policy(relationship.id, targetOrganizationId, fromPartner = true, memberResolution = true))

        val validation = service.validatePersonResolution(callerOrganizationId, targetOrganizationId, now)

        assertEquals(targetOrganizationId, validation.targetOrganization.id)
    }

    @Test
    fun `person attestation acceptance requires the attested account and active membership`()
    {
        val subjectAppUserId = UUID.randomUUID()
        val membershipId = UUID.randomUUID()
        val email = "member@partner.example"
        val relationship = configureEligiblePersonAcceptance()
        val membership = OrganizationMembership().apply {
            id = membershipId
            appUserId = subjectAppUserId
            organizationId = targetOrganizationId
        }
        whenever(membershipService.findActiveMembershipsByOrganizationAndExactEmail(targetOrganizationId, email))
            .thenReturn(listOf(membership))
        whenever(appUserService.getById(subjectAppUserId)).thenReturn(AppUser().apply {
            id = subjectAppUserId
            isActive = true
        })

        val validation = service.validatePersonAttestation(
            personAttestation(relationship.id, subjectAppUserId, membershipId, email),
            now,
        )

        assertEquals(membershipId, validation.membership.id)
    }

    @Test
    fun `person attestation acceptance fails closed when the account no longer maps to the membership`()
    {
        val subjectAppUserId = UUID.randomUUID()
        val membershipId = UUID.randomUUID()
        val email = "member@partner.example"
        val relationship = configureEligiblePersonAcceptance()
        whenever(membershipService.findActiveMembershipsByOrganizationAndExactEmail(targetOrganizationId, email))
            .thenReturn(listOf(OrganizationMembership().apply {
                id = membershipId
                appUserId = UUID.randomUUID()
                organizationId = targetOrganizationId
            }))

        assertThrows<OrganizationTrustNotFoundException> {
            service.validatePersonAttestation(
                personAttestation(relationship.id, subjectAppUserId, membershipId, email),
                now,
            )
        }
    }

    private fun configureEligiblePersonAcceptance(
        senderReviewDueAt: Instant? = null,
        targetReviewDueAt: Instant? = null,
    ): OrganizationTrustRelationship
    {
        val relationship = relationship()
        whenever(relationshipService.findCurrentForOrganizations(callerOrganizationId, targetOrganizationId))
            .thenReturn(relationship)
        whenever(relationshipService.isEffectivelySuspended(relationship.id)).thenReturn(false)
        whenever(organizationService.getOrganizationById(callerOrganizationId))
            .thenReturn(organization(callerOrganizationId))
        whenever(organizationService.getOrganizationById(targetOrganizationId))
            .thenReturn(organization(targetOrganizationId))
        whenever(policyService.getPolicy(relationship.id, callerOrganizationId))
            .thenReturn(
                policy(
                    relationship.id,
                    callerOrganizationId,
                    toPartner = true,
                    reviewDueAt = senderReviewDueAt,
                ),
            )
        whenever(policyService.getPolicy(relationship.id, targetOrganizationId))
            .thenReturn(
                policy(
                    relationship.id,
                    targetOrganizationId,
                    fromPartner = true,
                    reviewDueAt = targetReviewDueAt,
                ),
            )
        return relationship
    }

    private fun personAttestation(
        relationshipId: UUID,
        subjectAppUserId: UUID,
        membershipId: UUID,
        email: String,
    ): ExchangeRecipientAttestation = ExchangeRecipientAttestation().apply {
        exchangeRecipientId = UUID.randomUUID()
        this.relationshipId = relationshipId
        callerOrganizationId = this@TrustedRecipientValidationServiceTest.callerOrganizationId
        targetOrganizationId = this@TrustedRecipientValidationServiceTest.targetOrganizationId
        subjectType = ExchangeRecipientAttestationSubjectType.PERSON
        this.subjectAppUserId = subjectAppUserId
        subjectMembershipId = membershipId
        invitedEmailSnapshot = email
        organizationNameSnapshot = "Partner"
        verifiedAt = Timestamp.from(now.minusSeconds(60))
        verificationExpiresAt = Timestamp.from(now.plusSeconds(3600))
    }

    private fun configureEligibleTrust(
        senderAllows: Boolean = true,
        senderReviewDueAt: Instant? = null,
        targetReviewDueAt: Instant? = null,
    )
    {
        val relationship = relationship()
        whenever(relationshipService.findCurrentForOrganizations(callerOrganizationId, targetOrganizationId))
            .thenReturn(relationship)
        whenever(relationshipService.isEffectivelySuspended(relationship.id)).thenReturn(false)
        whenever(organizationService.getOrganizationById(callerOrganizationId))
            .thenReturn(organization(callerOrganizationId))
        whenever(organizationService.getOrganizationById(targetOrganizationId))
            .thenReturn(organization(targetOrganizationId))
        whenever(policyService.getPolicy(relationship.id, callerOrganizationId))
            .thenReturn(
                policy(
                    relationship.id,
                    callerOrganizationId,
                    toPartner = senderAllows,
                    reviewDueAt = senderReviewDueAt,
                ),
            )
        whenever(policyService.getPolicy(relationship.id, targetOrganizationId))
            .thenReturn(
                policy(
                    relationship.id,
                    targetOrganizationId,
                    fromPartner = true,
                    groupDiscovery = true,
                    memberResolution = true,
                    reviewDueAt = targetReviewDueAt,
                ),
            )
    }

    private fun relationship(): OrganizationTrustRelationship = OrganizationTrustRelationship().apply {
        organizationAId = callerOrganizationId
        organizationBId = targetOrganizationId
        requestedByOrganizationId = callerOrganizationId
        requestedByAppUserId = UUID.randomUUID()
        status = OrganizationTrustRelationshipStatus.ACTIVE
        requestedAt = Timestamp.from(now.minusSeconds(60))
        requestExpiresAt = Timestamp.from(now.plusSeconds(60))
        activatedAt = Timestamp.from(now.minusSeconds(30))
        reviewDueAt = Timestamp.from(now.plusSeconds(3600))
    }

    private fun policy(
        relationshipId: UUID,
        organizationId: UUID,
        toPartner: Boolean = false,
        fromPartner: Boolean = false,
        groupDiscovery: Boolean = false,
        memberResolution: Boolean = false,
        expiresAt: Instant? = null,
        reviewDueAt: Instant? = null,
    ): OrganizationTrustPartyPolicy = OrganizationTrustPartyPolicy().apply {
        this.relationshipId = relationshipId
        policyOwnerOrganizationId = organizationId
        allowExchangesToPartner = toPartner
        allowExchangesFromPartner = fromPartner
        allowPartnerGroupDiscovery = groupDiscovery
        allowPartnerMemberResolution = memberResolution
        this.expiresAt = expiresAt?.let(Timestamp::from)
        this.reviewDueAt = reviewDueAt?.let(Timestamp::from)
        updatedAt = Timestamp.from(now.minusSeconds(60))
    }

    private fun organization(id: UUID): Organization = Organization().apply {
        this.id = id
        name = "Organization $id"
        registrationNumber = id.toString()
        isActive = true
        verificationComplete = true
    }

    private fun publishedGroup(): PrincipalGroup = PrincipalGroup().apply {
        id = groupId
        name = "Published Group"
        scope = PrincipalGroupScope.ORG
        ownerOrganizationId = targetOrganizationId
        externallyPublished = true
        isActive = true
    }
}
