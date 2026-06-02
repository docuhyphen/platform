package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Org-level configuration for a third-party channel (Slack / Teams / WhatsApp).
 *
 * `enforced=true` means individual users cannot opt out via [NotificationPreference] —
 * the org has mandated this channel as the primary route. `fallbackChain` is a csv of
 * [NotificationChannelType] names used by the delivery dispatcher when the primary
 * channel fails (e.g. `SLACK,IN_APP,EMAIL`).
 */
@Entity
@Serializable
@Table(name = "organization_notification_channel")
class OrganizationNotificationChannel
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "organization_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var organizationId: UUID

    @Column(name = "channel", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var channel: NotificationChannelType = NotificationChannelType.SLACK

    @Column(name = "config_json", nullable = true, columnDefinition = "text")
    var configJson: String? = null

    @Column(name = "secret_ref", nullable = true)
    var secretRef: String? = null

    @Column(name = "enforced", nullable = false)
    var enforced: Boolean = false

    @Column(name = "fallback_chain", nullable = true, length = 256)
    var fallbackChain: String? = null

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

