package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.sql.Timestamp
import java.util.UUID

@ApplicationScoped
class OrganizationTrustRelationshipRepository :
    BaseRepository<OrganizationTrustRelationship>(OrganizationTrustRelationship::class.java)
{
    fun lockOrganizations(organizationAId: UUID, organizationBId: UUID)
    {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:organizationKey, 0))")
            .setParameter("organizationKey", "$organizationAId:$organizationBId")
            .singleResult
    }

    fun findCurrentForOrganizations(organizationAId: UUID, organizationBId: UUID): OrganizationTrustRelationship? =
        entityManager.createQuery(
            """SELECT r FROM OrganizationTrustRelationship r
               WHERE r.organizationAId = :organizationAId
                 AND r.organizationBId = :organizationBId
                 AND r.status IN :currentStatuses""",
            OrganizationTrustRelationship::class.java,
        )
            .setParameter("organizationAId", organizationAId)
            .setParameter("organizationBId", organizationBId)
            .setParameter(
                "currentStatuses",
                setOf(OrganizationTrustRelationshipStatus.PENDING, OrganizationTrustRelationshipStatus.ACTIVE),
            )
            .resultList
            .singleOrNull()

    fun findLatestTerminalForOrganizations(
        organizationAId: UUID,
        organizationBId: UUID,
    ): OrganizationTrustRelationship? =
        entityManager.createQuery(
            """SELECT r FROM OrganizationTrustRelationship r
               WHERE r.organizationAId = :organizationAId
                 AND r.organizationBId = :organizationBId
                 AND r.status IN :terminalStatuses
               ORDER BY r.requestedAt DESC, r.id DESC""",
            OrganizationTrustRelationship::class.java,
        )
            .setParameter("organizationAId", organizationAId)
            .setParameter("organizationBId", organizationBId)
            .setParameter(
                "terminalStatuses",
                setOf(
                    OrganizationTrustRelationshipStatus.REJECTED,
                    OrganizationTrustRelationshipStatus.WITHDRAWN,
                    OrganizationTrustRelationshipStatus.EXPIRED,
                    OrganizationTrustRelationshipStatus.ENDED,
                ),
            )
            .setMaxResults(1)
            .resultList
            .firstOrNull()

    fun findForParty(organizationId: UUID): List<OrganizationTrustRelationship> =
        entityManager.createQuery(
            """SELECT r FROM OrganizationTrustRelationship r
               WHERE r.organizationAId = :organizationId OR r.organizationBId = :organizationId
               ORDER BY r.requestedAt DESC, r.id DESC""",
            OrganizationTrustRelationship::class.java,
        )
            .setParameter("organizationId", organizationId)
            .resultList

    fun findDueForExpiryForUpdate(now: Timestamp, limit: Int = 100): List<OrganizationTrustRelationship> =
        entityManager.createQuery(
            """SELECT r FROM OrganizationTrustRelationship r
               WHERE r.status = :status AND r.requestExpiresAt <= :now
               ORDER BY r.requestExpiresAt ASC, r.id ASC""",
            OrganizationTrustRelationship::class.java,
        )
            .setParameter("status", OrganizationTrustRelationshipStatus.PENDING)
            .setParameter("now", now)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setMaxResults(limit.coerceIn(1, 500))
            .resultList

    fun insertAndFlush(relationship: OrganizationTrustRelationship): OrganizationTrustRelationship
    {
        entityManager.persist(relationship)
        entityManager.flush()
        return relationship
    }

    fun updateAndFlush(relationship: OrganizationTrustRelationship): OrganizationTrustRelationship
    {
        val updated = entityManager.merge(relationship)
        entityManager.flush()
        return updated
    }
}
