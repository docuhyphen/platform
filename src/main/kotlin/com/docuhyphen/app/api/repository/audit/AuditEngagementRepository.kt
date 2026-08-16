package com.docuhyphen.app.api.repository.audit

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.AuditEngagement
import com.docuhyphen.app.api.model.entity.AuditEngagementStatus
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.LockModeType
import java.sql.Timestamp
import java.util.UUID

@RequestScoped
class AuditEngagementRepository : BaseRepository<AuditEngagement>(AuditEngagement::class.java)
{
    fun findDueForExpiryForUpdate(now: Timestamp, limit: Int = 100): List<AuditEngagement>
    {
        return entityManager.createQuery(
            """SELECT e FROM AuditEngagement e
               WHERE e.status = :status
                 AND e.expiresAt <= :now""",
            AuditEngagement::class.java,
        )
            .setParameter("status", AuditEngagementStatus.ACTIVE)
            .setParameter("now", now)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setMaxResults(limit)
            .resultList
    }

    fun findByOrganization(organizationId: UUID?): List<AuditEngagement>
    {
        val jpql = if (organizationId == null)
        {
            "SELECT e FROM AuditEngagement e WHERE e.organizationId IS NULL ORDER BY e.requestedAt DESC"
        }
        else
        {
            "SELECT e FROM AuditEngagement e WHERE e.organizationId = :organizationId ORDER BY e.requestedAt DESC"
        }
        val query = entityManager.createQuery(jpql, AuditEngagement::class.java)
        if (organizationId != null)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.resultList
    }

    fun findActiveForPrincipal(
        organizationId: UUID?,
        resourceType: String?,
        resourceId: String?,
        auditorUserId: UUID,
        principalGroupIds: Set<UUID>,
        now: Timestamp,
    ): List<AuditEngagement>
    {
        val clauses = mutableListOf<String>()
        clauses += "e.status = :status"
        clauses += "e.startsAt <= :now"
        clauses += "e.expiresAt > :now"

        if (organizationId == null)
        {
            clauses += "e.organizationId IS NULL"
        }
        else
        {
            clauses += "e.organizationId = :organizationId"
        }
        clauses += buildString {
            append("(")
            append("e.auditorUserId = :auditorUserId")
            if (principalGroupIds.isNotEmpty())
            {
                append(" OR e.principalGroupId IN :principalGroupIds")
            }
            append(")")
        }

        if (resourceType != null)
        {
            clauses += "(e.resourceType IS NULL OR (e.resourceType = :resourceType AND e.resourceId = :resourceId))"
        }
        else
        {
            clauses += "e.resourceType IS NULL"
        }

        val query = entityManager.createQuery(
            "SELECT e FROM AuditEngagement e WHERE ${clauses.joinToString(" AND ")}",
            AuditEngagement::class.java,
        )
            .setParameter("status", AuditEngagementStatus.ACTIVE)
            .setParameter("now", now)
            .setParameter("auditorUserId", auditorUserId)

        if (organizationId != null)
        {
            query.setParameter("organizationId", organizationId)
        }
        if (principalGroupIds.isNotEmpty())
        {
            query.setParameter("principalGroupIds", principalGroupIds)
        }
        if (resourceType != null)
        {
            query.setParameter("resourceType", resourceType)
            query.setParameter("resourceId", resourceId)
        }

        return query.resultList
    }
}
