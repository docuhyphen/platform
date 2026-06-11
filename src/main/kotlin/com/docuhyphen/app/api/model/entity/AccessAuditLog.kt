package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Append-only audit record for sharing/access events (grants, revocations, denies,
 * state transitions). Distinct from the existing [DocumentAuditLog] (document I/O) and
 * [AuthAuditEvent] (authentication), which remain in place.
 *
 * Hash-chained, `eventHash = sha256(canonical(row) || prevEventHash)`, mirroring the
 * existing [AuthAuditEvent] design so the same WORM tooling can ingest it later.
 */
@Entity
@Serializable
@Table(name = "access_audit_log")
class AccessAuditLog
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    /** e.g. "SHARE_GRANT", "SHARE_REVOKE", "EXCHANGE_TRANSITION", "AUTHORIZE_DENY". */
    @Column(name = "action", nullable = false, length = 64)
    lateinit var action: String

    /** One of: ALLOW, DENY, GRANT, REVOKE, TRANSITION. */
    @Column(name = "outcome", nullable = false, length = 32)
    lateinit var outcome: String

    @Column(name = "actor_kind", nullable = false, length = 32)
    lateinit var actorKind: String

    @Column(name = "actor_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var actorId: UUID? = null

    @Column(name = "actor_email", nullable = true)
    var actorEmail: String? = null

    @Column(name = "target_resource_type", nullable = true, length = 32)
    var targetResourceType: String? = null

    @Column(name = "target_resource_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var targetResourceId: UUID? = null

    @Column(name = "target_principal_kind", nullable = true, length = 32)
    var targetPrincipalKind: String? = null

    @Column(name = "target_principal_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var targetPrincipalId: UUID? = null

    @Column(name = "organization_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var organizationId: UUID? = null

    @Column(name = "reason_code", nullable = true, length = 64)
    var reasonCode: String? = null

    @Column(name = "reason", nullable = true, length = 2048)
    var reason: String? = null

    @Column(name = "before_snapshot", nullable = true, columnDefinition = "text")
    var beforeSnapshot: String? = null

    @Column(name = "after_snapshot", nullable = true, columnDefinition = "text")
    var afterSnapshot: String? = null

    @Column(name = "event_hash", nullable = false, length = 128)
    lateinit var eventHash: String

    @Column(name = "prev_event_hash", nullable = true, length = 128)
    var prevEventHash: String? = null

    constructor()
}

