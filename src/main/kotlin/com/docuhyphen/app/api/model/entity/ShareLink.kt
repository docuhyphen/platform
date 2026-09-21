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
    /** Anyone-with-link content access. Resolves as a PUBLIC_LINK grant on its covering [Share]. */
    DIRECT_GRANT,

    /**
     * Issued to prove one respondent's recipient contact before a runtime request session is
     * minted. Never resolves as a content grant; its covering [Share] is a request-party Share,
     * not a content grant, and the central authorizer refuses it as a [Share] grant path.
     */
    VERIFICATION_BOOTSTRAP,
}

/**
 * Tokenised anonymous principal backing a [Share] with `principalKind = PUBLIC_LINK`.
 * One [Share] row + one [ShareLink] row per distinct link (anyone-with-link,
 * password-protected, domain-restricted, expiring, max-uses).
 *
 * The raw token is never stored; only its hash. The presenter sends the token, the
 * server hashes and looks up.
 */
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

    @Column(name = "password_hash", nullable = true)
    var passwordHash: String? = null

    @Column(name = "max_uses", nullable = true)
    var maxUses: Int? = null

    @Column(name = "used_count", nullable = false)
    var usedCount: Int = 0

    /** Comma-separated email domain allowlist. Null means any domain. */
    @Column(name = "domain_allowlist", nullable = true, length = 2048)
    var domainAllowlist: String? = null

    @Column(name = "require_mfa", nullable = false)
    var requireMfa: Boolean = false

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var status: ShareLinkStatus = ShareLinkStatus.ACTIVE

    @Column(name = "link_mode", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var linkMode: ShareLinkMode = ShareLinkMode.DIRECT_GRANT

    /**
     * Hash of the outstanding recipient contact-proof code for a [ShareLinkMode.VERIFICATION_BOOTSTRAP]
     * link. Scoped to this link alone, never to the parent Exchange, so verifying one respondent's
     * contact can never satisfy another's. Cleared once verified or replaced.
     */
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

    /** Set each time this link's secret is rotated in place. Null until the first rotation. */
    @Column(name = "rotated_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var rotatedAt: Timestamp? = null

    /** Number of times this link's secret has been rotated in place. */
    @Column(name = "rotation_count", nullable = false)
    var rotationCount: Int = 0

    /**
     * The bootstrap [ShareLink] this one replaced, if any. Null for a link issued directly rather
     * than as a replacement. Lets a respondent's link history be traced without recovering any
     * retired secret.
     */
    @Column(name = "replaces_share_link_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var replacesShareLinkId: UUID? = null

    @Column(name = "expires_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var expiresAt: Timestamp? = null

    @Column(name = "created_by_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var createdByAppUserId: UUID? = null

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

