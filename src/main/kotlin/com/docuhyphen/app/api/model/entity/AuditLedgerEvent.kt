package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "audit_ledger_event")
class AuditLedgerEvent
{
    @Id
    var id: UUID = UUID.randomUUID()

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

    @Column(name = "stream_id", nullable = false, length = 128)
    lateinit var streamId: String

    @Column(name = "stream_sequence", nullable = false)
    var streamSequence: Long = 0

    @Column(name = "actor_kind", nullable = false, length = 32)
    lateinit var actorKind: String

    @Column(name = "actor_id")
    var actorId: UUID? = null

    @Column(name = "actor_role", length = 64)
    var actorRole: String? = null

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

    @Column(name = "owner_type", nullable = false, length = 32)
    var ownerType: String = "PLATFORM"

    @Column(name = "owner_id")
    var ownerId: UUID? = null

    @Column(name = "organization_label", length = 256)
    var organizationLabel: String? = null

    @Column(name = "target_type", length = 64)
    var targetType: String? = null

    @Column(name = "target_id", length = 128)
    var targetId: String? = null

    @Column(name = "target_label", length = 256)
    var targetLabel: String? = null

    @Column(name = "reason", length = 2048)
    var reason: String? = null

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    var payloadJson: String = "{}"

    @Column(name = "prev_hash", length = 128)
    var prevHash: String? = null

    @Column(name = "event_hash", nullable = false, length = 128)
    lateinit var eventHash: String

    @Column(name = "signing_key_id", length = 64)
    var signingKeyId: String? = null

    @Column(name = "checkpoint_ref", length = 128)
    var checkpointRef: String? = null
}
