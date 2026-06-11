package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Per-owner personal contact entry. Populated only when a exchange recipient
 * accepts (reciprocity gate). One row per (owner, contactEmail) pair; both sides of
 * an accepted relationship get a row.
 */
@Entity
@Serializable
@Table(
    name = "user_contact",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_contact_owner_email", columnNames = ["owner_app_user_id", "contact_email"])
    ],
    indexes = [
        Index(name = "ix_user_contact_owner_last_shared", columnList = "owner_app_user_id, last_shared_at"),
    ],
)
class UserContact
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "owner_app_user_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var ownerAppUserId: UUID

    @Column(name = "contact_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var contactAppUserId: UUID? = null

    @Column(name = "contact_email", nullable = false)
    lateinit var contactEmail: String

    @Column(name = "contact_first_name", nullable = true)
    var contactFirstName: String? = null

    @Column(name = "contact_last_name", nullable = true)
    var contactLastName: String? = null

    @Column(name = "first_shared_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var firstSharedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "last_shared_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var lastSharedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "share_count", nullable = false)
    var shareCount: Int = 0

    @Column(name = "last_exchange_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var lastSessionId: UUID? = null

    constructor()
}
