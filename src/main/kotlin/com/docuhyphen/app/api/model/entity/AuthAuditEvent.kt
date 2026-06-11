package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "auth_audit_event")
class AuthAuditEvent
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "actor_id")
    var actorId: UUID? = null

    @Column(name = "actor_role")
    var actorRole: String? = null

    @Column(name = "target_type")
    var targetType: String? = null

    @Column(name = "target_id")
    var targetId: String? = null

    @Column(name = "action", nullable = false)
    var action: String = ""

    @Column(name = "outcome", nullable = false)
    var outcome: String = ""

    @Column(name = "reason_code")
    var reasonCode: String? = null

    @Column(name = "exchange_id")
    var sessionId: String? = null

    @Column(name = "organization_id")
    var organizationId: UUID? = null

    @Column(name = "request_id")
    var requestId: String? = null

    @Column(name = "action_reason", length = 2048)
    var actionReason: String? = null

    @Column(name = "before_snapshot", length = 4000)
    var beforeSnapshot: String? = null

    @Column(name = "after_snapshot", length = 4000)
    var afterSnapshot: String? = null

    @Column(name = "created_date", nullable = false)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "event_hash", nullable = false, length = 128)
    var eventHash: String = ""

    @Column(name = "prev_event_hash", length = 128)
    var prevEventHash: String? = null
}


