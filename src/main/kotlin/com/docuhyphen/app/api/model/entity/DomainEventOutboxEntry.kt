package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "workflow_event_outbox")
class DomainEventOutboxEntry
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: UUID = UUID.randomUUID()

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 256)
    lateinit var idempotencyKey: String

    @Column(name = "event_type", nullable = false, length = 128)
    lateinit var eventType: String


    @Column(name = "owner_kind", nullable = false, length = 32)
    var ownerKind: String = "PLATFORM"

    @Column(name = "owner_id")
    var ownerId: UUID? = null

    @Column(name = "ordering_key", length = 160)
    var orderingKey: String? = null

    @Column(name = "sequence_number", insertable = false, updatable = false)
    var sequenceNumber: Long? = null

    @Column(name = "envelope_json", nullable = false, columnDefinition = "text")
    lateinit var envelopeJson: String

    @Column(name = "status", nullable = false, length = 32)
    var status: String = STATUS_PENDING

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "delivered_at")
    var deliveredAt: Timestamp? = null

    @Column(name = "last_error", length = 1024)
    var lastError: String? = null

    companion object
    {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_DELIVERED = "DELIVERED"
        const val STATUS_FAILED = "FAILED"
    }
}
