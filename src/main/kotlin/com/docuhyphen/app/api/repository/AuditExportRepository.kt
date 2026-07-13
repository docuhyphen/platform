package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportStatus
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.LockModeType
import java.sql.Timestamp
import java.util.UUID

@RequestScoped
class AuditExportRepository : BaseRepository<AuditExport>(AuditExport::class.java)
{
    fun listForOrganization(organizationId: UUID?, platformOnly: Boolean): List<AuditExport>
    {
        val jpql = if (platformOnly)
        {
            "SELECT e FROM AuditExport e WHERE e.organizationId IS NULL ORDER BY e.requestedAt DESC"
        }
        else
        {
            "SELECT e FROM AuditExport e WHERE e.organizationId = :organizationId ORDER BY e.requestedAt DESC"
        }
        val query = entityManager.createQuery(jpql, AuditExport::class.java)
        if (!platformOnly)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.resultList
    }

    /**
     * `BUILDING` exports eligible to be claimed for an archive build, oldest first: those with no
     * build-claim lease yet, or whose lease has expired (a node died mid-build). Does not itself
     * lock or claim anything - the caller must lock and re-check each candidate before acting on it.
     */
    fun findDueForBuilding(now: Timestamp, limit: Int = 50): List<AuditExport>
    {
        return entityManager.createQuery(
            """SELECT e FROM AuditExport e
               WHERE e.status = :status AND (e.buildLeaseExpiresAt IS NULL OR e.buildLeaseExpiresAt <= :now)
               ORDER BY e.updatedAt ASC""",
            AuditExport::class.java,
        )
            .setParameter("status", AuditExportStatus.BUILDING)
            .setParameter("now", now)
            .setMaxResults(limit)
            .resultList
    }

    fun findDueForExpiryForUpdate(now: Timestamp, limit: Int = 100): List<AuditExport>
    {
        return entityManager.createQuery(
            """SELECT e FROM AuditExport e
               WHERE e.status = :status AND e.expiresAt IS NOT NULL AND e.expiresAt <= :now""",
            AuditExport::class.java,
        )
            .setParameter("status", AuditExportStatus.READY)
            .setParameter("now", now)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setMaxResults(limit)
            .resultList
    }

    fun insert(export: AuditExport): AuditExport = save(export)
}
