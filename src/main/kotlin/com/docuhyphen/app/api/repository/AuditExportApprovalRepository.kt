package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AuditExportApproval
import jakarta.enterprise.context.RequestScoped
import java.util.UUID

@RequestScoped
class AuditExportApprovalRepository : BaseRepository<AuditExportApproval>(AuditExportApproval::class.java)
{
    fun findByExport(exportId: UUID): List<AuditExportApproval>
    {
        return entityManager.createQuery(
            "SELECT a FROM AuditExportApproval a WHERE a.exportId = :exportId ORDER BY a.approvedAt ASC",
            AuditExportApproval::class.java,
        )
            .setParameter("exportId", exportId)
            .resultList
    }

    fun hasApprovalFrom(exportId: UUID, approvedByUserId: UUID): Boolean
    {
        val count = entityManager.createQuery(
            "SELECT COUNT(a) FROM AuditExportApproval a WHERE a.exportId = :exportId AND a.approvedByUserId = :approvedByUserId",
            Long::class.javaObjectType,
        )
            .setParameter("exportId", exportId)
            .setParameter("approvedByUserId", approvedByUserId)
            .singleResult
        return count > 0
    }

    /**
     * The authoritative distinct-approver count for an export, read from this append-only table
     * rather than a mutable counter on the export row, so concurrent approvals cannot lose an
     * increment to a lost-update race.
     */
    fun countByExport(exportId: UUID): Long
    {
        return entityManager.createQuery(
            "SELECT COUNT(a) FROM AuditExportApproval a WHERE a.exportId = :exportId",
            Long::class.javaObjectType,
        )
            .setParameter("exportId", exportId)
            .singleResult
    }

    fun insert(approval: AuditExportApproval): AuditExportApproval = save(approval)
}
