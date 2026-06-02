package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Audit row written by `DeliveryDispatcher` after every send attempt. Drives retry
 * logic, fallback chains, and per-channel delivery dashboards.
 */
@Entity
@Serializable
@Table(name = "notification_delivery_log")
class NotificationDeliveryLog
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "event_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var eventId: UUID

    @Column(name = "event_type", nullable = false, length = 128)
    lateinit var eventType: String

    @Column(name = "app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var appUserId: UUID? = null

    @Column(name = "channel", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var channel: NotificationChannelType = NotificationChannelType.IN_APP

    @Column(name = "outcome", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var outcome: NotificationDeliveryOutcome = NotificationDeliveryOutcome.DELIVERED

    @Column(name = "attempt_number", nullable = false)
    var attemptNumber: Int = 1

    @Column(name = "error_message", nullable = true, length = 2048)
    var errorMessage: String? = null

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

