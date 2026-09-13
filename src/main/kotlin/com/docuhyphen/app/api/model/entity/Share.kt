package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class ShareSource
{
    /** Explicit grant to a known principal. */
    DIRECT,

    /** Email invite that materialises an [ExternalParticipant] on acceptance. */
    INVITE,

    /** Anonymous access via a [ShareLink] token. */
    LINK,

    /** Auto-generated when a [PrincipalGroup] receives a DIRECT share. */
    INHERITED_FROM_GROUP,

    /** Auto-generated when an [Organization] receives a DIRECT share. */
    INHERITED_FROM_ORG,
}

enum class ShareStatus
{
    PENDING_APPROVAL,
    ACTIVE,
    REVOKED,
    EXPIRED,
}

/**
 * Unified sharing grant. Replaces the legacy three-way recipient columns on
 * [Exchange] (`recipient_id`, `recipient_type`, `group_id`) and the
 * boolean `allow_document_*` flags. Every recipient, be it a user, participant,
 * group, organization, service account, or public link, gets its own row.
 *
 * Inheritance is materialised: when a group is shared with, a DIRECT row is created
 * for the group plus INHERITED_FROM_GROUP rows for each member, with `sourceShareId`
 * pointing back to the parent grant. This keeps the [AuthorizationService] query a
 * single `select * from share where ...` instead of recursive traversal.
 */
@Entity
@Serializable
@Table(name = "share")
class Share
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "resource_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var resourceType: ResourceType = ResourceType.EXCHANGE

    @Column(name = "resource_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var resourceId: UUID

    @Column(name = "principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var principalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "principal_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var principalId: UUID

    @Column(name = "role_name", nullable = false, length = 64)
    var roleName: String = ExchangeShareRoleName.VIEWER.name

    @Column(name = "source", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var source: ShareSource = ShareSource.DIRECT

    @Column(name = "source_share_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var sourceShareId: UUID? = null

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var status: ShareStatus = ShareStatus.ACTIVE

    @Column(name = "granted_by_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var grantedByAppUserId: UUID? = null

    @Column(name = "granted_by_principal_kind", nullable = true, length = 32)
    @Enumerated(EnumType.STRING)
    var grantedByPrincipalKind: PrincipalKind? = null

    @Column(name = "granted_by_principal_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var grantedByPrincipalId: UUID? = null

    @Column(name = "granted_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var grantedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "expires_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var expiresAt: Timestamp? = null

    @Column(name = "revoked_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var revokedAt: Timestamp? = null

    @Column(name = "revoked_by_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var revokedByAppUserId: UUID? = null

    @Column(name = "revoked_by_principal_kind", nullable = true, length = 32)
    @Enumerated(EnumType.STRING)
    var revokedByPrincipalKind: PrincipalKind? = null

    @Column(name = "revoked_by_principal_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var revokedByPrincipalId: UUID? = null

    /**
     * Optional JSON blob describing per-share constraints
     * (can_reshare, can_download, watermark, ip_allowlist, require_mfa, max_views, etc.).
     * Parsed by [com.docuhyphen.app.api.service.auth.authz.AuthorizationService] when
     * computing denies.
     */
    @Column(name = "constraints_json", nullable = true, columnDefinition = "text")
    var constraintsJson: String? = null

    constructor()

    fun exchangeRoleName(): ExchangeShareRoleName =
        runCatching { ExchangeShareRoleName.valueOf(roleName) }
            .getOrElse { throw IllegalStateException("Share $id does not carry an Exchange role key: $roleName") }
}

