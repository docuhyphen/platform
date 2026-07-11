package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Durable, transactional record of a workflow engine domain event. Written by
 * [com.docuhyphen.app.api.service.workflow.WorkflowEventOutboxPublisher] in the same transaction as
 * the authoritative workflow state mutation that produced it, so the event intent survives a crash
 * between the state commit and event routing. A background dispatcher
 * ([com.docuhyphen.app.api.service.workflow.WorkflowEventDispatcher]) claims committed rows after
 * commit, routes them, marks them delivered, and retries transient failures with bounded backoff.
 *
 * Denormalized identifiers only (no `@ManyToOne` FK to a business entity), so a subject or instance
 * can be changed or removed without orphaning pending event intent.
 */
@Entity
@Table(name = "workflow_event_outbox")
class WorkflowEventOutboxEntry
{
    @Id
    var id: UUID = UUID.randomUUID()

    /** Stable identifier of the logical event; matches the enqueued envelope's own event id. */
    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: UUID = UUID.randomUUID()

    /** Dedup key so a retried enqueue for the same occurrence writes at most one row. */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 256)
    lateinit var idempotencyKey: String

    @Column(name = "event_type", nullable = false, length = 128)
    lateinit var eventType: String

    @Column(name = "organization_id")
    var organizationId: UUID? = null

    /** JSON-serialized [com.docuhyphen.app.api.service.notification.DomainEvent] envelope. */
    @Column(name = "envelope_json", nullable = false, columnDefinition = "text")
    lateinit var envelopeJson: String

    /** PENDING until routed; DELIVERED on success; FAILED only for a non-retryable decode error. */
    @Column(name = "status", nullable = false, length = 32)
    var status: String = STATUS_PENDING

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    /** Earliest time a dispatcher may claim this row; advanced by backoff after a failed attempt. */
    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "delivered_at")
    var deliveredAt: Timestamp? = null

    /** Safe, human-readable summary of the last delivery failure. Never contains payload contents. */
    @Column(name = "last_error", length = 1024)
    var lastError: String? = null

    companion object
    {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_DELIVERED = "DELIVERED"
        const val STATUS_FAILED = "FAILED"
    }
}
