package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Persisted in-app notification (the inbox). Written by the [InAppChannel] delivery bean
 * and pushed to connected WebSocket clients (iteration 5 wires the WS endpoint).
 */
@Entity
@Serializable
@Table(name = "in_app_notification")
class InAppNotification
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "app_user_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var appUserId: UUID

    @Column(name = "event_type", nullable = false, length = 128)
    lateinit var eventType: String

    @Column(name = "title", nullable = false)
    lateinit var title: String

    @Column(name = "body", nullable = true, length = 2048)
    var body: String? = null

    @Column(name = "payload_json", nullable = true, columnDefinition = "text")
    var payloadJson: String? = null

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "read_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var readAt: Timestamp? = null

    constructor()
}

