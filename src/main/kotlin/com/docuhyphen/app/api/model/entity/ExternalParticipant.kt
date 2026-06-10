package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Persistent identity for an email-only recipient (no platform login). Replaces the
 * ad-hoc "EMAIL recipient" pattern that previously lived as columns on [SharingSession]
 * (`recipient_otp_hash`, `recipient_otp_expiry`, `no_auth_access_*`).
 *
 * Scoping:
 * - `ownerOrganizationId != null` : participant belongs to the directory of that org.
 * - `ownerOrganizationId == null` : "personal" participant created by an individual
 *   [AppUser] for ad-hoc sharing outside any org.
 *
 * Uniqueness: per owner (org or "PERSONAL" partition) on lower-cased email, enforced by
 * a partial unique index in V8. Cross-tenant merging is intentionally an explicit admin
 * operation, never implicit.
 */
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

    @Column(name = "email", nullable = false)
    lateinit var email: String

    /** Lower-cased copy of [email] for portable case-insensitive uniqueness. */
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

