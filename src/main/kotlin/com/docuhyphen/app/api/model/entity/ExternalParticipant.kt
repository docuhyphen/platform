package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Serializable
@Table(name = "external_participant")
class ExternalParticipant
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "owner_organization_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var ownerOrganizationId: UUID? = null

    @Column(name = "owner_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var ownerAppUserId: UUID? = null

    @Column(name = "email", nullable = false)
    lateinit var email: String

    @Column(name = "email_lower", nullable = false)
    lateinit var emailLower: String

    @Column(name = "display_name", nullable = true)
    var displayName: String? = null

    @Column(name = "email_verified_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var emailVerifiedAt: Timestamp? = null

    @Column(name = "last_seen_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var lastSeenAt: Timestamp? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

