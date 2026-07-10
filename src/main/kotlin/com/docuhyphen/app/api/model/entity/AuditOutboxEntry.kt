package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Durable, immutable audit intent written by
 * [com.docuhyphen.app.api.service.audit.AuditRecorder] in the same transaction as the business
 * change it records (transactional-outbox pattern; Phase 1 of
 * `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`).
 *
 * Denormalized IDs/labels only, following the [AccessAuditLog] pattern - never the
 * [DocumentAuditLog] anti-pattern of a non-null `@ManyToOne` FK to a mutable business entity.
 * A business entity (Exchange, document, user, ...) can be deleted or changed without cascading
 * into or orphaning this row.
 *
 * Append-only: migration `V41__audit_outbox.sql` installs a trigger that unconditionally denies
 * `UPDATE`/`DELETE` on this table. Phase 2's ledger processor must not mutate outbox rows; it
 * reads them and derives ledger state elsewhere.
 */
@Entity
@Table(name = "audit_outbox")
class AuditOutboxEntry
{
    @Id
    var id: UUID = UUID.randomUUID()

    /** Stable identifier for the logical occurrence; stays constant across capture retries. */
    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: UUID = UUID.randomUUID()

    /** Dedup key so a retried capture attempt for the same occurrence writes at most one row. */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 256)
    lateinit var idempotencyKey: String

    @Column(name = "event_type_key", nullable = false, length = 128)
    lateinit var eventTypeKey: String

    @Column(name = "category", nullable = false, length = 32)
    lateinit var category: String

    @Column(name = "outcome", nullable = false, length = 32)
    lateinit var outcome: String

    @Column(name = "actor_id")
    var actorId: UUID? = null

    @Column(name = "actor_role", length = 64)
    var actorRole: String? = null

    /** Human-readable actor identification (name/email), added in `V48__audit_denormalized_labels.sql`. */
    @Column(name = "actor_label", length = 256)
    var actorLabel: String? = null

    /**
     * Explicit actor classification (`HUMAN`/`APP`/`PUBLIC_LINK`/`WORKFLOW`/`SYSTEM`), added in
     * `V43__audit_outbox_actor_kind.sql`. Null for call sites written before Phase 3 task 1;
     * [com.docuhyphen.app.api.service.audit.LedgerProcessor.resolveActorKind] prefers this value
     * when present and only falls back to guessing from [actorId] presence when it is null.
     */
    @Column(name = "actor_kind", length = 32)
    var actorKind: String? = null

    @Column(name = "target_type", length = 64)
    var targetType: String? = null

    @Column(name = "target_id", length = 128)
    var targetId: String? = null

    /** Human-readable target identification (document/Exchange/person name, etc), added in `V48__audit_denormalized_labels.sql`. */
    @Column(name = "target_label", length = 256)
    var targetLabel: String? = null

    @Column(name = "organization_id")
    var organizationId: UUID? = null

    /** Human-readable organization name, added in `V48__audit_denormalized_labels.sql`. */
    @Column(name = "organization_label", length = 256)
    var organizationLabel: String? = null

    @Column(name = "session_id", length = 128)
    var sessionId: String? = null

    @Column(name = "reason", length = 2048)
    var reason: String? = null

    /** JSON-encoded [AuditEventDraft.payload]; already validated against the prohibited-key list. */
    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    var payloadJson: String = "{}"

    @Column(name = "server_trace_id", length = 64)
    var serverTraceId: String? = null

    @Column(name = "correlation_id", length = 64)
    var correlationId: String? = null

    @Column(name = "causation_id", length = 64)
    var causationId: String? = null

    /** Denormalized business-transaction reference (e.g. the Exchange ID), no FK constraint. */
    @Column(name = "business_transaction_id", length = 128)
    var businessTransactionId: String? = null

    /** PENDING at insert time in Phase 1; Phase 2 defines how draining is tracked. */
    @Column(name = "status", nullable = false, length = 32)
    var status: String = "PENDING"

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "catalog_version", nullable = false)
    var catalogVersion: Int = 0
}
