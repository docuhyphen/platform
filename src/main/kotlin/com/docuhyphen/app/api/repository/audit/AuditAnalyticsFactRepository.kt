package com.docuhyphen.app.api.repository.audit

import com.docuhyphen.app.api.model.entity.AuditAnalyticsFact
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.RequestScoped
import java.util.*

@RequestScoped
class AuditAnalyticsFactRepository : BaseRepository<AuditAnalyticsFact>(AuditAnalyticsFact::class.java)
{
    fun existsByLedgerEventId(ledgerEventId: UUID): Boolean
    {
        val count = entityManager.createQuery(
            "SELECT COUNT(f) FROM AuditAnalyticsFact f WHERE f.ledgerEventId = :ledgerEventId",
            Long::class.javaObjectType,
        )
            .setParameter("ledgerEventId", ledgerEventId)
            .singleResult
        return count > 0
    }

    fun countAll(): Long
    {
        return entityManager.createQuery("SELECT COUNT(f) FROM AuditAnalyticsFact f", Long::class.javaObjectType).singleResult
    }

    fun countByOrganization(organizationId: UUID?, platformOnly: Boolean): Long
    {
        val jpql = if (platformOnly)
        {
            "SELECT COUNT(f) FROM AuditAnalyticsFact f WHERE f.ownerType = 'PLATFORM'"
        }
        else
        {
            "SELECT COUNT(f) FROM AuditAnalyticsFact f WHERE f.ownerType = 'ORGANIZATION' AND f.ownerId = :organizationId"
        }
        val query = entityManager.createQuery(jpql, Long::class.javaObjectType)
        if (!platformOnly)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.singleResult
    }

    /** Ledger event ids missing a fact row, for reconciliation diagnostics; bounded by [limit]. */
    fun findMissingLedgerEventIds(organizationId: UUID?, platformOnly: Boolean, limit: Int): List<UUID>
    {
        val where = mutableListOf("NOT EXISTS (SELECT 1 FROM AuditAnalyticsFact f WHERE f.ledgerEventId = l.eventId)")
        if (platformOnly)
        {
            where += "l.ownerType = 'PLATFORM'"
        }
        else
        {
            where += "l.ownerType = 'ORGANIZATION' AND l.ownerId = :organizationId"
        }

        val query = entityManager.createQuery(
            "SELECT l.eventId FROM AuditLedgerEvent l WHERE ${where.joinToString(" AND ")} ORDER BY l.recordedAt ASC",
            UUID::class.java,
        )
            .setMaxResults(limit)
        if (!platformOnly)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.resultList
    }

    fun deleteAllByOrganization(organizationId: UUID?, platformOnly: Boolean): Int
    {
        val jpql = if (platformOnly)
        {
            "DELETE FROM AuditAnalyticsFact f WHERE f.ownerType = 'PLATFORM'"
        }
        else
        {
            "DELETE FROM AuditAnalyticsFact f WHERE f.ownerType = 'ORGANIZATION' AND f.ownerId = :organizationId"
        }
        val query = entityManager.createQuery(jpql)
        if (!platformOnly)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.executeUpdate()
    }

    fun insert(fact: AuditAnalyticsFact): AuditAnalyticsFact = save(fact)
}
