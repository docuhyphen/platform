package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * One provisional or finalized unit of capacity spent against a [RequestExecutionGrant].
 *
 * [reservationKey] makes a reservation idempotent: a caller retrying the same logical reservation
 * (the same party, the same upload) after a timeout or crash gets back the row it already created
 * rather than double-spending the grant's capacity.
 */
@Entity
@Table(name = "request_execution_usage_reservation")
class RequestExecutionUsageReservation
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "grant_id", nullable = false)
    lateinit var grantId: UUID

    @Column(name = "usage_kind", nullable = false, length = 32)
    lateinit var usageKind: String

    @Column(name = "reservation_key", nullable = false, length = 200)
    lateinit var reservationKey: String

    @Column(name = "quantity", nullable = false)
    var quantity: Long = 0

    @Column(name = "status", nullable = false, length = 16)
    lateinit var status: String

    @Column(name = "reserved_at", nullable = false)
    lateinit var reservedAt: Timestamp

    @Column(name = "consumed_at")
    var consumedAt: Timestamp? = null

    @Column(name = "released_at")
    var releasedAt: Timestamp? = null

    @Column(name = "rolled_back_at")
    var rolledBackAt: Timestamp? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())
}
