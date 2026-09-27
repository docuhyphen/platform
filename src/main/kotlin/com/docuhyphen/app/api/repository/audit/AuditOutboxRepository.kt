package com.docuhyphen.app.api.repository.audit

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.audit.AuditTargetQuery
import com.docuhyphen.app.api.model.entity.AuditOutboxEntry
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.NoResultException
import java.sql.Timestamp

@RequestScoped
class AuditOutboxRepository : BaseRepository<AuditOutboxEntry>(AuditOutboxEntry::class.java)
{
    /**
     * Looks up an existing outbox row by idempotency key. [AuditRecorder] uses this before
     * inserting so a retried capture attempt for the same occurrence does not write a duplicate
     * row; the `idempotency_key` unique constraint is the authoritative belt-and-braces guard.
     */
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

    /**
     * Inserts [entry] in the caller's active transaction (no `REQUIRES_NEW`): it must commit or
     * roll back atomically with the business mutation that produced it. Intentionally does not
     * reuse [BaseRepository.save]'s own `@Transactional` semantics beyond the default `REQUIRED`
     * propagation already provided there.
     */
    fun insert(entry: AuditOutboxEntry): AuditOutboxEntry = save(entry)

    /**
     * Batch of the oldest [limit] not-yet-ledgered outbox rows by `recorded_at`, for
     * [com.docuhyphen.app.api.service.audit.LedgerProcessor] to drain. `audit_outbox` rows are
     * never mutated to mark them "processed" (append-only trigger denies it - see
     * `V41__audit_outbox.sql`), so "already drained" is determined by a `NOT EXISTS` check against
     * `audit_ledger_event` directly in this query rather than by an outbox status flag.
     *
     * This exclusion is load-bearing, not an optimization: without it, a batch of already-ledgered
     * rows sitting at the head of the queue would permanently occupy
     * every drain pass's fixed-size batch, starving every newer row behind them forever, since nothing
     * ever advances a cursor/offset - each pass re-fetches the same unconditional "oldest N" rows.
     * [com.docuhyphen.app.api.service.audit.LedgerProcessor.appendOne]'s own
     * `existsByEventId` recheck stays in place as the authoritative concurrent-drain guard; this
     * query's exclusion only needs to be good enough to keep a drain pass making forward progress.
     */
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

    fun findForTargets(owner: AuditOwnerScope?, targetType: String, targetIds: Set<String>, query: AuditTargetQuery): List<AuditOutboxEntry>
    {
        val (clauses, parameters) = targetClauses(owner, targetType, targetIds, query)
        val order = if (query.newestFirst) "DESC" else "ASC"
        val typed = entityManager.createQuery(
            "SELECT a FROM AuditOutboxEntry a WHERE ${clauses.joinToString(" AND ")} ORDER BY a.occurredAt $order, a.recordedAt $order, a.eventId $order",
            AuditOutboxEntry::class.java,
        )
        parameters.forEach { (name, value) -> typed.setParameter(name, value) }
        return typed.setFirstResult(query.offset).setMaxResults(query.limit).resultList
    }

    fun countForTargets(owner: AuditOwnerScope?, targetType: String, targetIds: Set<String>, query: AuditTargetQuery): Long
    {
        val (clauses, parameters) = targetClauses(owner, targetType, targetIds, query)
        val typed = entityManager.createQuery(
            "SELECT COUNT(a) FROM AuditOutboxEntry a WHERE ${clauses.joinToString(" AND ")}",
            Long::class.javaObjectType,
        )
        parameters.forEach { (name, value) -> typed.setParameter(name, value) }
        return typed.singleResult
    }

    private fun targetClauses(
        owner: AuditOwnerScope?,
        targetType: String,
        targetIds: Set<String>,
        query: AuditTargetQuery,
    ): Pair<List<String>, Map<String, Any>>
    {
        val clauses = mutableListOf("a.targetType = :targetType")
        val parameters = mutableMapOf<String, Any>("targetType" to targetType)
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
