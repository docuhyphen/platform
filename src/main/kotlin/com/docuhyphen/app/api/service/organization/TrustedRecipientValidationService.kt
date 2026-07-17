package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestationSubjectType
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.service.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant
import java.util.UUID

data class TrustedExchangePolicyValidation(
    val relationship: OrganizationTrustRelationship,
    val callerOrganization: Organization,
    val targetOrganization: Organization,
    val senderPolicy: OrganizationTrustPartyPolicy,
    val targetPolicy: OrganizationTrustPartyPolicy,
    val verificationExpiresAt: Instant,
)

data class TrustedGroupValidation(
    val relationship: OrganizationTrustRelationship,
    val callerOrganization: Organization,
    val targetOrganization: Organization,
    val senderPolicy: OrganizationTrustPartyPolicy,
    val targetPolicy: OrganizationTrustPartyPolicy,
    val verificationExpiresAt: Instant,
    val group: PrincipalGroup,
)

data class TrustedPersonAcceptanceValidation(
    val relationship: OrganizationTrustRelationship,
    val membership: OrganizationMembership,
)

@ApplicationScoped
class TrustedRecipientValidationService @Inject constructor(
    private val relationshipService: OrganizationTrustRelationshipService,
    private val policyService: OrganizationTrustPolicyService,
    private val organizationService: OrganizationService,
    private val organizationGroupService: OrganizationGroupService,
    private val membershipService: OrganizationMembershipService,
    private val appUserService: AppUserService,
)
{
    fun relationshipIdForLookup(
        callerOrganizationId: UUID,
        targetOrganizationId: UUID,
    ): UUID? = relationshipService.findCurrentForOrganizations(
        callerOrganizationId,
        targetOrganizationId,
    )?.id

    fun validateGroupDiscovery(
        callerOrganizationId: UUID,
        targetOrganizationId: UUID,
        now: Instant = Instant.now(),
    ): TrustedExchangePolicyValidation = validatePolicies(
        callerOrganizationId,
        targetOrganizationId,
        requireGroupDiscovery = true,
        requireMemberResolution = false,
        now = now,
    )

    fun validatePersonResolution(
        callerOrganizationId: UUID,
        targetOrganizationId: UUID,
        now: Instant = Instant.now(),
    ): TrustedExchangePolicyValidation = validatePolicies(
        callerOrganizationId,
        targetOrganizationId,
        requireGroupDiscovery = false,
        requireMemberResolution = true,
        now = now,
    )

    fun validateGroupSelection(
        callerOrganizationId: UUID,
        targetOrganizationId: UUID,
        groupId: UUID,
        now: Instant = Instant.now(),
    ): TrustedGroupValidation
    {
        val policyValidation = validatePolicies(
            callerOrganizationId,
            targetOrganizationId,
            requireGroupDiscovery = true,
            requireMemberResolution = false,
            now = now,
        )
        val group = organizationGroupService.getPublishedExchangeGroup(targetOrganizationId, groupId)
            ?: unavailable()
        return policyValidation.withGroup(group)
    }

    fun validateGroupAttestation(
        attestation: ExchangeRecipientAttestation,
        now: Instant = Instant.now(),
    ): TrustedGroupValidation
    {
        if (attestation.subjectType != ExchangeRecipientAttestationSubjectType.GROUP)
        {
            unavailable()
        }
        val groupId = attestation.subjectGroupId ?: unavailable()
        val validation = validateGroupSelection(
            attestation.callerOrganizationId,
            attestation.targetOrganizationId,
            groupId,
            now,
        )
        if (validation.relationship.id != attestation.relationshipId)
        {
            unavailable()
        }
        return validation
    }

    fun isGroupAttestationCurrentlyEligible(attestation: ExchangeRecipientAttestation): Boolean =
        runCatching { validateGroupAttestation(attestation) }.isSuccess

    /**
     * Revalidates a trusted-person attestation at acceptance time. The current relationship, both
     * party Exchange-direction policies, the target organization, the attested account, and the
     * exact target membership must all still be eligible; otherwise it fails closed.
     */
    fun validatePersonAttestation(
        attestation: ExchangeRecipientAttestation,
        now: Instant = Instant.now(),
    ): TrustedPersonAcceptanceValidation
    {
        if (attestation.subjectType != ExchangeRecipientAttestationSubjectType.PERSON)
        {
            unavailable()
        }
        val subjectAppUserId = attestation.subjectAppUserId ?: unavailable()
        val email = attestation.invitedEmailSnapshot ?: unavailable()
        val validation = validatePolicies(
            attestation.callerOrganizationId,
            attestation.targetOrganizationId,
            requireGroupDiscovery = false,
            requireMemberResolution = false,
            now = now,
        )
        if (validation.relationship.id != attestation.relationshipId)
        {
            unavailable()
        }
        val membership = membershipService.findActiveMembershipsByOrganizationAndExactEmail(
            attestation.targetOrganizationId,
            email,
        ).singleOrNull() ?: unavailable()
        if (membership.appUserId != subjectAppUserId ||
            membership.deprovisionedAt != null ||
            membership.expiresAt?.toInstant()?.isAfter(now) == false)
        {
            unavailable()
        }
        val appUser = runCatching { appUserService.getById(subjectAppUserId) }.getOrNull() ?: unavailable()
        if (!appUser.isActive || appUser.deprovisionedAt != null)
        {
            unavailable()
        }
        return TrustedPersonAcceptanceValidation(validation.relationship, membership)
    }

    fun isPersonAttestationCurrentlyEligible(attestation: ExchangeRecipientAttestation): Boolean =
        runCatching { validatePersonAttestation(attestation) }.isSuccess

    private fun validatePolicies(
        callerOrganizationId: UUID,
        targetOrganizationId: UUID,
        requireGroupDiscovery: Boolean,
        requireMemberResolution: Boolean,
        now: Instant,
    ): TrustedExchangePolicyValidation
    {
        if (callerOrganizationId == targetOrganizationId)
        {
            unavailable()
        }
        val relationship = relationshipService.findCurrentForOrganizations(
            callerOrganizationId,
            targetOrganizationId,
        ) ?: unavailable()
        if (relationship.status != OrganizationTrustRelationshipStatus.ACTIVE ||
            relationshipService.isEffectivelySuspended(relationship.id))
        {
            unavailable()
        }

        val callerOrganization = eligibleOrganization(callerOrganizationId)
        val targetOrganization = eligibleOrganization(targetOrganizationId)
        val senderPolicy = policyService.getPolicy(relationship.id, callerOrganizationId)
        val targetPolicy = policyService.getPolicy(relationship.id, targetOrganizationId)
        if (!senderPolicy.allowExchangesToPartner || !targetPolicy.allowExchangesFromPartner ||
            (requireGroupDiscovery && !targetPolicy.allowPartnerGroupDiscovery) ||
            (requireMemberResolution && !targetPolicy.allowPartnerMemberResolution) ||
            isExpired(senderPolicy, now) || isExpired(targetPolicy, now))
        {
            unavailable()
        }

        val verificationExpiresAt = listOfNotNull(
            relationship.reviewDueAt?.toInstant(),
            senderPolicy.expiresAt?.toInstant(),
            targetPolicy.expiresAt?.toInstant(),
        ).minOrNull()?.takeIf { it.isAfter(now) } ?: unavailable()

        return TrustedExchangePolicyValidation(
            relationship,
            callerOrganization,
            targetOrganization,
            senderPolicy,
            targetPolicy,
            verificationExpiresAt,
        )
    }

    private fun eligibleOrganization(organizationId: UUID): Organization
    {
        val organization = runCatching { organizationService.getOrganizationById(organizationId) }
            .getOrElse { unavailable() }
        if (!organization.isActive || !organization.verificationComplete)
        {
            unavailable()
        }
        return organization
    }

    private fun isExpired(policy: OrganizationTrustPartyPolicy, now: Instant): Boolean =
        policy.expiresAt?.toInstant()?.isAfter(now) == false

    private fun TrustedExchangePolicyValidation.withGroup(group: PrincipalGroup): TrustedGroupValidation =
        TrustedGroupValidation(
            relationship,
            callerOrganization,
            targetOrganization,
            senderPolicy,
            targetPolicy,
            verificationExpiresAt,
            group,
        )

    private fun unavailable(): Nothing =
        throw OrganizationTrustNotFoundException("Published trusted group is unavailable")
}
