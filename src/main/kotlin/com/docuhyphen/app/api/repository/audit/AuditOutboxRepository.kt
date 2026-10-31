package com.docuhyphen.app.api.repository.audit

import com.docuhyphen.app.api.model.audit.AuditTargetQuery
import com.docuhyphen.app.api.model.entity.AuditOutboxEntry
import com.docuhyphen.app.api.repository.BaseRepository
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.NoResultException
import java.sql.Timestamp

@RequestScoped
class AuditOutboxRepository : BaseRepository<AuditOutboxEntry>(AuditOutboxEntry::class.java)
{
    fun findByIdempotencyKey(idempotencyKey: String): AuditOutboxEntry?
    {
        return try
        {
            entityManager.createQuery(
                "SELECT a FROM AuditOutboxEntry a WHERE a.idempotencyKey = :idempotencyKey",
                AuditOutboxEntry::class.java,
            )
                .setParameter("idempotencyKey", idempotencyKey)
                .singleResult
        }
        catch (e: NoResultException)
        {
            null
        }
    }

    fun insert(entry: AuditOutboxEntry): AuditOutboxEntry = save(entry)

    fun findOldestUnledgeredByRecordedAt(limit: Int): List<AuditOutboxEntry>
    {
        return entityManager.createQuery(
            "SELECT a FROM AuditOutboxEntry a WHERE NOT EXISTS " +
                    "(SELECT 1 FROM AuditLedgerEvent l WHERE l.eventId = a.eventId) " +
                    "ORDER BY a.recordedAt ASC",
            AuditOutboxEntry::class.java,
        )
            .setMaxResults(limit.coerceIn(1, 5000))
            .resultList
    }

    fun findForTargets(
        owner: AuditOwnerScope?,
        targetTypes: Set<String>,
        targetIds: Set<String>,
        query: AuditTargetQuery
    ): List<AuditOutboxEntry>
    {
        val (clauses, parameters) = targetClauses(owner, targetTypes, targetIds, query)
        val order = if (query.newestFirst) "DESC" else "ASC"
        val typed = entityManager.createQuery(
            "SELECT a FROM AuditOutboxEntry a WHERE ${clauses.joinToString(" AND ")} ORDER BY a.occurredAt $order, a.recordedAt $order, a.eventId $order",
            AuditOutboxEntry::class.java,
        )
        parameters.forEach { (name, value) -> typed.setParameter(name, value) }
        return typed.setFirstResult(query.offset).setMaxResults(query.limit).resultList
    }

    fun countForTargets(
        owner: AuditOwnerScope?,
        targetTypes: Set<String>,
        targetIds: Set<String>,
        query: AuditTargetQuery
    ): Long
    {
        val (clauses, parameters) = targetClauses(owner, targetTypes, targetIds, query)
        val typed = entityManager.createQuery(
            "SELECT COUNT(a) FROM AuditOutboxEntry a WHERE ${clauses.joinToString(" AND ")}",
            Long::class.javaObjectType,
        )
        parameters.forEach { (name, value) -> typed.setParameter(name, value) }
        return typed.singleResult
    }

    private fun targetClauses(
        owner: AuditOwnerScope?,
        targetTypes: Set<String>,
        targetIds: Set<String>,
        query: AuditTargetQuery,
    ): Pair<List<String>, Map<String, Any>>
    {
        require(targetTypes.isNotEmpty()) { "A target search names at least one target type" }
        val clauses = mutableListOf("a.targetType IN :targetTypes")
        val parameters = mutableMapOf<String, Any>("targetTypes" to targetTypes)
        when (owner)
        {
            null -> Unit
            AuditOwnerScope.Platform -> clauses += "a.ownerType = 'PLATFORM'"
            is AuditOwnerScope.Organization ->
            {
                clauses += "a.ownerType = 'ORGANIZATION' AND a.ownerId = :ownerId"
                parameters["ownerId"] = owner.organizationId
            }

            is AuditOwnerScope.Personal ->
            {
                clauses += "a.ownerType = 'USER' AND a.ownerId = :ownerId"
                parameters["ownerId"] = owner.userId
            }
        }
        if (targetIds.isNotEmpty())
        {
            clauses += "a.targetId IN :targetIds"
            parameters["targetIds"] = targetIds
        }
        query.eventTypePrefix?.let {
            clauses += "a.eventTypeKey LIKE :eventTypePrefix"
            parameters["eventTypePrefix"] = "$it%"
        }
        if (query.eventTypeKeys.isNotEmpty())
        {
            clauses += "a.eventTypeKey IN :eventTypeKeys"
            parameters["eventTypeKeys"] = query.eventTypeKeys
        }
        query.actorId?.let {
            clauses += "a.actorId = :actorId"
            parameters["actorId"] = it
        }
        query.occurredAfter?.let {
            clauses += "a.occurredAt >= :occurredAfter"
            parameters["occurredAfter"] = Timestamp.from(it)
        }
        query.occurredBefore?.let {
            clauses += "a.occurredAt < :occurredBefore"
            parameters["occurredBefore"] = Timestamp.from(it)
        }
        return clauses to parameters
    }
}
