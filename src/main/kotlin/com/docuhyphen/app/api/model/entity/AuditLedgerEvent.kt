package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * One ordered, hash-chained ledger row, appended by
 * [com.docuhyphen.app.api.service.audit.LedgerProcessor] from a committed `audit_outbox` row
 * and its integrity metadata.
 *
 * Denormalized IDs/labels only, same rule as [AuditOutboxEntry]/[AccessAuditLog] - never the
 * [DocumentAuditLog] anti-pattern of a non-null `@ManyToOne` FK to a mutable business entity.
 *
 * Append-only: migration `V42__audit_ledger.sql` installs a trigger that unconditionally denies
 * `UPDATE`/`DELETE` on this table.
 */
@Entity
@Table(name = "audit_ledger_event")
class AuditLedgerEvent
{
    @Id
    var id: UUID = UUID.randomUUID()

    /** Denormalized from [AuditOutboxEntry.eventId]; no FK. Unique - idempotency key for draining. */
    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: UUID = UUID.randomUUID()

    @Column(name = "event_type_key", nullable = false, length = 128)
    lateinit var eventTypeKey: String

    @Column(name = "category", nullable = false, length = 32)
    lateinit var category: String

    @Column(name = "outcome", nullable = false, length = 32)
    lateinit var outcome: String

    @Column(name = "schema_version", nullable = false)
    var schemaVersion: Int = 0

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "ledger_time", nullable = false)
    var ledgerTime: Timestamp = Timestamp.from(Instant.now())

    /** Owner-scope + time-partition identifier this event was appended to; see [LedgerProcessor]. */
    @Column(name = "stream_id", nullable = false, length = 128)
    lateinit var streamId: String

    /** Strictly increasing per [streamId], assigned atomically via `stream_head`. */
    @Column(name = "stream_sequence", nullable = false)
    var streamSequence: Long = 0

    /** One of: HUMAN, APP, SYSTEM, PUBLIC_LINK, WORKFLOW. */
    @Column(name = "actor_kind", nullable = false, length = 32)
    lateinit var actorKind: String

    @Column(name = "actor_id")
    var actorId: UUID? = null

    @Column(name = "actor_role", length = 64)
    var actorRole: String? = null

    /** Human-readable actor identification (name/email), added in `V48__audit_denormalized_labels.sql`. */
    @Column(name = "actor_label", length = 256)
    var actorLabel: String? = null

    @Column(name = "session_id", length = 128)
    var sessionId: String? = null

    @Column(name = "server_trace_id", length = 64)
    var serverTraceId: String? = null

    @Column(name = "correlation_id", length = 64)
    var correlationId: String? = null

    @Column(name = "causation_id", length = 64)
    var causationId: String? = null

    @Column(name = "organization_id")
    var organizationId: UUID? = null

    /** Human-readable organization name, added in `V48__audit_denormalized_labels.sql`. */
    @Column(name = "organization_label", length = 256)
    var organizationLabel: String? = null

    @Column(name = "target_type", length = 64)
    var targetType: String? = null

    @Column(name = "target_id", length = 128)
    var targetId: String? = null

    /** Human-readable target identification (document/Exchange/person name, etc), added in `V48__audit_denormalized_labels.sql`. */
    @Column(name = "target_label", length = 256)
    var targetLabel: String? = null

    @Column(name = "reason", length = 2048)
    var reason: String? = null

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    var payloadJson: String = "{}"

    /** Null only for the first event ever appended to a stream. */
    @Column(name = "prev_hash", length = 128)
    var prevHash: String? = null

    /** `sha256(canonicalEvent || streamSequence || prevHash)`; see [LedgerProcessor.computeHash]. */
    @Column(name = "event_hash", nullable = false, length = 128)
    lateinit var eventHash: String

    /** Identifies the key used to sign the archived segment; null before archival. */
    @Column(name = "signing_key_id", length = 64)
    var signingKeyId: String? = null

    @Column(name = "checkpoint_ref", length = 128)
    var checkpointRef: String? = null
}
