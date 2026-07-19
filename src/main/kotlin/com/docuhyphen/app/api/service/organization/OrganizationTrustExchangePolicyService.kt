package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class OrganizationTrustExchangePolicyService @Inject constructor(
    private val relationshipService: OrganizationTrustRelationshipService,
    private val policyService: OrganizationTrustPolicyService,
    private val organizationService: OrganizationService,
)
{
    fun permitsExchange(
        senderOrganizationId: UUID,
        receiverOrganizationId: UUID,
        now: Instant = Instant.now(),
    ): Boolean
    {
        if (senderOrganizationId == receiverOrganizationId)
        {
            return true
        }
        val relationship = relationshipService.findCurrentForOrganizations(
            senderOrganizationId,
            receiverOrganizationId,
        ) ?: return false
        if (relationship.status != OrganizationTrustRelationshipStatus.ACTIVE ||
            relationship.reviewDueAt?.toInstant()?.isAfter(now) != true ||
            relationshipService.isEffectivelySuspended(relationship.id))
        {
            return false
        }

        val senderOrganization = try
        {
            organizationService.getOrganizationById(senderOrganizationId)
        }
        catch (_: OrganizationNotFoundException)
        {
            return false
        }
        val receiverOrganization = try
        {
            organizationService.getOrganizationById(receiverOrganizationId)
        }
        catch (_: OrganizationNotFoundException)
        {
            return false
        }
        if (!senderOrganization.isActive || !senderOrganization.verificationComplete ||
            !receiverOrganization.isActive || !receiverOrganization.verificationComplete)
        {
            return false
        }

        val senderPolicy = findPolicy(relationship.id, senderOrganizationId) ?: return false
        val receiverPolicy = findPolicy(relationship.id, receiverOrganizationId) ?: return false
        return senderPolicy.allowExchangesToPartner &&
            receiverPolicy.allowExchangesFromPartner &&
            isCurrent(senderPolicy, now) &&
            isCurrent(receiverPolicy, now)
    }

    private fun findPolicy(
        relationshipId: UUID,
        ownerOrganizationId: UUID,
    ): OrganizationTrustPartyPolicy? = try
    {
        policyService.getPolicy(relationshipId, ownerOrganizationId)
    }
    catch (_: OrganizationTrustNotFoundException)
    {
        null
    }

    private fun isCurrent(policy: OrganizationTrustPartyPolicy, now: Instant): Boolean =
        policy.expiresAt?.toInstant()?.isAfter(now) != false &&
            policy.reviewDueAt?.toInstant()?.isAfter(now) != false
}
