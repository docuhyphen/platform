package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class ShareLinkStatus
{
    ACTIVE,
    REVOKED,
    EXPIRED,
}

enum class ShareLinkMode
{
    DIRECT_GRANT,

    VERIFICATION_BOOTSTRAP,
}

@Entity
@Serializable
@Table(name = "share_link")
class ShareLink
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "share_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var shareId: UUID

    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    lateinit var tokenHash: String

    @Column(name = "max_uses", nullable = true)
    var maxUses: Int? = null

    @Column(name = "used_count", nullable = false)
    var usedCount: Int = 0

    @Column(name = "require_mfa", nullable = false)
    var requireMfa: Boolean = false

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var status: ShareLinkStatus = ShareLinkStatus.ACTIVE

    @Column(name = "link_mode", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var linkMode: ShareLinkMode = ShareLinkMode.DIRECT_GRANT

    @Column(name = "contact_otp_hash", nullable = true)
    var contactOtpHash: String? = null

    @Column(name = "contact_otp_expires_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var contactOtpExpiresAt: Timestamp? = null

    @Column(name = "contact_otp_failed_attempts", nullable = false)
    var contactOtpFailedAttempts: Int = 0

    @Column(name = "contact_otp_challenge_count", nullable = false)
    var contactOtpChallengeCount: Int = 0

    @Column(name = "contact_otp_locked_until", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var contactOtpLockedUntil: Timestamp? = null

    @Column(name = "rotated_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var rotatedAt: Timestamp? = null

    @Column(name = "rotation_count", nullable = false)
    var rotationCount: Int = 0

    @Column(name = "replaces_share_link_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var replacesShareLinkId: UUID? = null

    @Column(name = "expires_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var expiresAt: Timestamp? = null

    @Column(name = "created_by_principal_kind", nullable = true, length = 32)
    @Enumerated(EnumType.STRING)
    var createdByPrincipalKind: PrincipalKind? = null

    @Column(name = "created_by_principal_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var createdByPrincipalId: UUID? = null

    @Column(name = "verification_strength", nullable = true, length = 32)
    @Enumerated(EnumType.STRING)
    var verificationStrength: RequestAccessSessionVerificationStrength? = null

    @Column(name = "last_used_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var lastUsedAt: Timestamp? = null

    @Column(name = "revoked_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var revokedAt: Timestamp? = null

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

