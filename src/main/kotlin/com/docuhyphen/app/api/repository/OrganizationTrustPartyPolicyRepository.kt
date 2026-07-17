package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class OrganizationTrustPartyPolicyRepository :
    BaseRepository<OrganizationTrustPartyPolicy>(OrganizationTrustPartyPolicy::class.java)
{
    fun findByRelationship(relationshipId: UUID): List<OrganizationTrustPartyPolicy> =
        entityManager.createQuery(
            """SELECT p FROM OrganizationTrustPartyPolicy p
               WHERE p.relationshipId = :relationshipId
               ORDER BY p.policyOwnerOrganizationId ASC""",
            OrganizationTrustPartyPolicy::class.java,
        )
            .setParameter("relationshipId", relationshipId)
            .resultList

    fun findByRelationshipAndOwner(
        relationshipId: UUID,
        policyOwnerOrganizationId: UUID,
    ): OrganizationTrustPartyPolicy? =
        entityManager.createQuery(
            """SELECT p FROM OrganizationTrustPartyPolicy p
               WHERE p.relationshipId = :relationshipId
                 AND p.policyOwnerOrganizationId = :policyOwnerOrganizationId""",
            OrganizationTrustPartyPolicy::class.java,
        )
            .setParameter("relationshipId", relationshipId)
            .setParameter("policyOwnerOrganizationId", policyOwnerOrganizationId)
            .resultList
            .singleOrNull()

    fun insertAllAndFlush(policies: Collection<OrganizationTrustPartyPolicy>): List<OrganizationTrustPartyPolicy>
    {
        policies.forEach(entityManager::persist)
        entityManager.flush()
        return policies.toList()
    }

    fun updateAndFlush(policy: OrganizationTrustPartyPolicy): OrganizationTrustPartyPolicy
    {
        val updated = entityManager.merge(policy)
        entityManager.flush()
        return updated
    }
}
