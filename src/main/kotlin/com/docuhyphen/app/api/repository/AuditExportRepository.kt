package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportStatus
import jakarta.enterprise.context.RequestScoped
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

    /** Exports awaiting build, oldest first, so a backlog is worked in request order. */
    fun findByStatus(status: AuditExportStatus, limit: Int = 50): List<AuditExport>
    {
        return entityManager.createQuery(
            "SELECT e FROM AuditExport e WHERE e.status = :status ORDER BY e.updatedAt ASC",
            AuditExport::class.java,
        )
            .setParameter("status", status)
            .setMaxResults(limit)
            .resultList
    }

    fun findDueForExpiry(now: Timestamp, limit: Int = 100): List<AuditExport>
    {
        return entityManager.createQuery(
            """SELECT e FROM AuditExport e
               WHERE e.status = :status AND e.expiresAt IS NOT NULL AND e.expiresAt <= :now""",
            AuditExport::class.java,
        )
            .setParameter("status", AuditExportStatus.READY)
            .setParameter("now", now)
            .setMaxResults(limit)
            .resultList
    }

    fun insert(export: AuditExport): AuditExport = save(export)
}
