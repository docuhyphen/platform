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

