package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AuditEngagement
import com.docuhyphen.app.api.model.entity.AuditEngagementStatus
import jakarta.enterprise.context.RequestScoped
import java.sql.Timestamp
import java.util.UUID

@RequestScoped
class AuditEngagementRepository : BaseRepository<AuditEngagement>(AuditEngagement::class.java)
{
    fun findDueForExpiry(now: Timestamp): List<AuditEngagement>
    {
        return entityManager.createQuery(
            """SELECT e FROM AuditEngagement e
               WHERE e.status = :status
                 AND e.expiresAt <= :now""",
            AuditEngagement::class.java,
        )
            .setParameter("status", AuditEngagementStatus.ACTIVE)
            .setParameter("now", now)
            .resultList
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
