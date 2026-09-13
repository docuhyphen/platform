package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * One denormalized dimensional fact per ledger event, keyed by [ledgerEventId] so projection is
 * idempotent under retry/rebuild. Deliberately carries no payload column - only dimensions safe
 * for aggregation - so it can never copy forward a prohibited field.
 */
@Entity
@Table(name = "audit_analytics_fact")
class AuditAnalyticsFact
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "ledger_event_id", nullable = false, unique = true)
    var ledgerEventId: UUID = UUID.randomUUID()

    @Column(name = "organization_id")
    var organizationId: UUID? = null

    @Column(name = "owner_type", nullable = false, length = 32)
    var ownerType: String = "PLATFORM"

    @Column(name = "owner_id")
    var ownerId: UUID? = null

    @Column(name = "stream_id", nullable = false, length = 128)
    lateinit var streamId: String

    @Column(name = "category", nullable = false, length = 32)
    lateinit var category: String

    @Column(name = "event_type_key", nullable = false, length = 128)
    lateinit var eventTypeKey: String

    @Column(name = "outcome", nullable = false, length = 32)
    lateinit var outcome: String

    @Column(name = "actor_kind", nullable = false, length = 32)
    lateinit var actorKind: String

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "occurred_date", nullable = false)
    var occurredDate: LocalDate = LocalDate.now()

    @Column(name = "schema_version", nullable = false)
    var schemaVersion: Int = 0

    @Column(name = "projected_at", nullable = false)
    var projectedAt: Timestamp = Timestamp.from(Instant.now())
}
