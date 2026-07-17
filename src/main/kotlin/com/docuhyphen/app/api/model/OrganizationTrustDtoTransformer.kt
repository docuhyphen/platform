package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.OrganizationTrustPolicyDto
import com.docuhyphen.app.api.model.dto.OrganizationTrustRelationshipDto
import com.docuhyphen.app.api.model.dto.OrganizationDirectoryEntryDto
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.OrganizationTrustSuspension
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class OrganizationTrustDtoTransformer
{
    fun toDirectoryEntryDto(organization: Organization): OrganizationDirectoryEntryDto =
        OrganizationDirectoryEntryDto(
            id = organization.id,
            name = organization.name,
        )

    fun toRelationshipDto(
        relationship: OrganizationTrustRelationship,
        currentOrganizationId: UUID,
        partner: Organization,
        activeSuspensions: List<OrganizationTrustSuspension>,
    ): OrganizationTrustRelationshipDto
    {
        val currentSuspension = activeSuspensions.firstOrNull {
            it.suspendingOrganizationId == currentOrganizationId
        }
        return OrganizationTrustRelationshipDto(
            id = relationship.id,
            currentOrganizationId = currentOrganizationId,
            partnerOrganizationId = partner.id,
            partnerOrganizationName = partner.name,
            partnerOrganizationActive = partner.isActive,
            partnerOrganizationVerified = partner.verificationComplete,
            requestedByCurrentOrganization = relationship.requestedByOrganizationId == currentOrganizationId,
            status = relationship.status,
            requestMessage = relationship.requestMessage,
            requestedAt = relationship.requestedAt,
            requestExpiresAt = relationship.requestExpiresAt,
            activatedAt = relationship.activatedAt,
            endedAt = relationship.endedAt,
            reviewDueAt = relationship.reviewDueAt,
            version = relationship.version,
            effectivelySuspended = activeSuspensions.isNotEmpty(),
            suspendedByCurrentOrganization = currentSuspension != null,
            suspendedByPartner = activeSuspensions.any {
                it.suspendingOrganizationId != currentOrganizationId
            },
            currentOrganizationSuspensionId = currentSuspension?.id,
        )
    }

    fun toPolicyDto(
        policy: OrganizationTrustPartyPolicy,
        currentOrganizationId: UUID,
        ownerOrganizationName: String,
    ): OrganizationTrustPolicyDto = OrganizationTrustPolicyDto(
        id = policy.id,
        relationshipId = policy.relationshipId,
        policyOwnerOrganizationId = policy.policyOwnerOrganizationId,
        policyOwnerOrganizationName = ownerOrganizationName,
        ownedByCurrentOrganization = policy.policyOwnerOrganizationId == currentOrganizationId,
        allowExchangesToPartner = policy.allowExchangesToPartner,
        allowExchangesFromPartner = policy.allowExchangesFromPartner,
        allowPartnerMemberResolution = policy.allowPartnerMemberResolution,
        allowPartnerGroupDiscovery = policy.allowPartnerGroupDiscovery,
        shareMemberDisplayName = policy.shareMemberDisplayName,
        expiresAt = policy.expiresAt,
        reviewDueAt = policy.reviewDueAt,
        revision = policy.revision,
        updatedAt = policy.updatedAt,
    )
}
