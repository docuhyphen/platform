package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Per-user OAuth link binding an [AppUser] to their external account inside a configured
 * [OrganizationNotificationChannel] (e.g. Slack user id `U02ABCD...`).
 */
@Entity
@Serializable
@Table(name = "user_channel_link")
class UserChannelLink
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "app_user_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var appUserId: UUID

    @Column(name = "organization_notification_channel_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var organizationNotificationChannelId: UUID

    @Column(name = "external_account_id", nullable = false)
    lateinit var externalAccountId: String

    @Column(name = "secret_ref", nullable = true)
    var secretRef: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

