package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.OrganizationTrustSuspension
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class OrganizationTrustSuspensionRepository :
    BaseRepository<OrganizationTrustSuspension>(OrganizationTrustSuspension::class.java)
{
    fun findActive(relationshipId: UUID): List<OrganizationTrustSuspension> =
        entityManager.createQuery(
            """SELECT s FROM OrganizationTrustSuspension s
               WHERE s.relationshipId = :relationshipId AND s.clearedAt IS NULL
               ORDER BY s.suspendedAt ASC, s.id ASC""",
            OrganizationTrustSuspension::class.java,
        )
            .setParameter("relationshipId", relationshipId)
            .resultList

    fun findActiveByOwner(
        relationshipId: UUID,
        suspendingOrganizationId: UUID,
    ): OrganizationTrustSuspension? =
        entityManager.createQuery(
            """SELECT s FROM OrganizationTrustSuspension s
               WHERE s.relationshipId = :relationshipId
                 AND s.suspendingOrganizationId = :suspendingOrganizationId
                 AND s.clearedAt IS NULL""",
            OrganizationTrustSuspension::class.java,
        )
            .setParameter("relationshipId", relationshipId)
            .setParameter("suspendingOrganizationId", suspendingOrganizationId)
            .resultList
            .singleOrNull()

    fun insertAndFlush(suspension: OrganizationTrustSuspension): OrganizationTrustSuspension
    {
        entityManager.persist(suspension)
        entityManager.flush()
        return suspension
    }

    fun updateAndFlush(suspension: OrganizationTrustSuspension): OrganizationTrustSuspension
    {
        val updated = entityManager.merge(suspension)
        entityManager.flush()
        return updated
    }
}
