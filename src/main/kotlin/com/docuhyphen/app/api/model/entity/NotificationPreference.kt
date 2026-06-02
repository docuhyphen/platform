package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Per-user preferences for which channels receive which event patterns, with optional
 * quiet hours and digest batching.
 *
 * Replaces the legacy notification booleans on [AppUserSettings] (which remain in place
 * but become advisory once the rule engine is the source of truth in iteration 4+).
 */
@Entity
@Serializable
@Table(name = "notification_preference")
class NotificationPreference
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "app_user_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var appUserId: UUID

    /** Glob over the event taxonomy: `session.*`, `document.viewed`, `*`. */
    @Column(name = "event_pattern", nullable = false, length = 128)
    lateinit var eventPattern: String

    /** Comma-separated [NotificationChannelType] names. */
    @Column(name = "channels", nullable = false, length = 512)
    lateinit var channels: String

    @Column(name = "delivery", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var delivery: NotificationDelivery = NotificationDelivery.INSTANT

    @Column(name = "quiet_hours_start", nullable = true, length = 8)
    var quietHoursStart: String? = null

    @Column(name = "quiet_hours_end", nullable = true, length = 8)
    var quietHoursEnd: String? = null

    @Column(name = "timezone", nullable = true, length = 64)
    var timezone: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

