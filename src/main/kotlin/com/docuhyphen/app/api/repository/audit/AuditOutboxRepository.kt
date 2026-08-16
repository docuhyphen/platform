package com.docuhyphen.app.api.repository.audit

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.AuditOutboxEntry
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.NoResultException

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
}
