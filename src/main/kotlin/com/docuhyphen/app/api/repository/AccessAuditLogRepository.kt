package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AccessAuditLog
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class AccessAuditLogRepository : BaseRepository<AccessAuditLog>(AccessAuditLog::class.java)
{
    /** Latest entry in the chain (used to compute `prevEventHash` for the next append). */
    fun findLatest(): AccessAuditLog? =
        entityManager.createQuery(
            "SELECT a FROM AccessAuditLog a ORDER BY a.createdDate DESC",
            AccessAuditLog::class.java,
        )
            .setMaxResults(1)
            .resultList
            .firstOrNull()

    fun findForResource(resourceType: String, resourceId: UUID, limit: Int = 100): List<AccessAuditLog> =
        entityManager.createQuery(
            """SELECT a FROM AccessAuditLog a
               WHERE a.targetResourceType = :rt AND a.targetResourceId = :rid
               ORDER BY a.createdDate DESC""",
            AccessAuditLog::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setMaxResults(limit)
            .resultList
}

