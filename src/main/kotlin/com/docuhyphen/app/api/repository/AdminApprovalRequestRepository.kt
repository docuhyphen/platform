package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AdminApprovalRequest
import com.docuhyphen.app.api.model.entity.AdminApprovalStatus
import jakarta.enterprise.context.RequestScoped
import java.time.Instant
import java.util.UUID

@RequestScoped
class AdminApprovalRequestRepository : BaseRepository<AdminApprovalRequest>(AdminApprovalRequest::class.java)
{
    fun findByApprovalId(id: UUID): AdminApprovalRequest?
    {
        return entityManager.createQuery(
            "SELECT a FROM AdminApprovalRequest a WHERE a.id = :id",
            AdminApprovalRequest::class.java,
        )
            .setParameter("id", id)
            .resultList
            .firstOrNull()
    }

    fun expirePendingApprovals(now: Instant): Int
    {
        return entityManager.createQuery(
            "UPDATE AdminApprovalRequest a SET a.status = :expired WHERE a.status = :pending AND a.expiresAt < :now",
        )
            .setParameter("expired", AdminApprovalStatus.EXPIRED)
            .setParameter("pending", AdminApprovalStatus.PENDING)
            .setParameter("now", java.sql.Timestamp.from(now))
            .executeUpdate()
    }
}

