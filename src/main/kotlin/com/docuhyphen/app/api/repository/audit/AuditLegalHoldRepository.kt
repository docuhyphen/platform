package com.docuhyphen.app.api.repository.audit

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.AuditLegalHold
import com.docuhyphen.app.api.model.entity.AuditLegalHoldStatus
import jakarta.enterprise.context.RequestScoped
import java.util.UUID

@RequestScoped
class AuditLegalHoldRepository : BaseRepository<AuditLegalHold>(AuditLegalHold::class.java)
{
    fun findActiveForResource(organizationId: UUID?, resourceType: String, resourceId: String): List<AuditLegalHold>
    {
        val where = mutableListOf("h.status = :status", "h.resourceType = :resourceType", "h.resourceId = :resourceId")
        if (organizationId != null)
        {
            where += "(h.organizationId IS NULL OR h.organizationId = :organizationId)"
        }

        val query = entityManager.createQuery(
            "SELECT h FROM AuditLegalHold h WHERE ${where.joinToString(" AND ")}",
            AuditLegalHold::class.java,
        )
            .setParameter("status", AuditLegalHoldStatus.ACTIVE)
            .setParameter("resourceType", resourceType)
            .setParameter("resourceId", resourceId)
        if (organizationId != null)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.resultList
    }

    fun findActiveForOrganization(organizationId: UUID?): List<AuditLegalHold>
    {
        val jpql = if (organizationId == null)
        {
            "SELECT h FROM AuditLegalHold h WHERE h.status = :status AND h.organizationId IS NULL"
        }
        else
        {
            "SELECT h FROM AuditLegalHold h WHERE h.status = :status AND h.organizationId = :organizationId"
        }
        val query = entityManager.createQuery(jpql, AuditLegalHold::class.java)
            .setParameter("status", AuditLegalHoldStatus.ACTIVE)
        if (organizationId != null)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.resultList
    }
}
