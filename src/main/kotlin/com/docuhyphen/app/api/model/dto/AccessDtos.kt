package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

/**
 * Current-session contract returned by GET /app-user/session.
 *
 * Carries user identity, the explicitly selected organization (null when the caller has not
 * selected one), all applicable scoped roles for the active context, and the union of effective
 * capabilities derived from those roles. The frontend drives menu visibility, action controls,
 * and settings tabs from [capabilities] rather than from raw role strings.
 *
 * Capabilities are computed server-side at request time from live role assignments so stale role
 * changes are reflected within the next session fetch.
 *
 * [subscription] describes the commercial position of the paying subject for this context: the
 * authenticated user when acting personally, or the selected organization when one is active. It
 * is recomputed on every call, so switching organization changes it without reissuing a token.
 * It is null only when the subscription could not be resolved, which must never widen access.
 */
@Serializable
data class CurrentSessionDto(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val email: String,
    val appRoles: List<String>,
    @Serializable(with = UUIDSerializer::class)
    val activeOrganizationId: UUID?,
    val organizationRoles: List<String>,
    val capabilities: List<String>,
    val availableOrganizations: List<SessionOrganizationOptionDto>,
    val idleTimeoutMinutes: Long,
    val subscription: EffectiveSubscriptionDto? = null,
)

/**
 * One organization the caller can act within, surfaced on the session so the frontend can
 * auto-select (single membership) or present a picker (multiple memberships). [isPrimary] is
 * informational only and does not gate auto-selection.
 */
@Serializable
data class SessionOrganizationOptionDto(
    @Serializable(with = UUIDSerializer::class)
    val organizationId: UUID,
    val name: String,
    val isPrimary: Boolean,
    val roles: List<String>,
)

/**
 * One entry in a resource's unified access view. Sourced from the `share`
 * table, replaces the legacy three-way recipient + per-session permission representation.
 */
@Serializable
data class SessionAccessEntryDto(
    @Serializable(with = UUIDSerializer::class)
    val shareId: UUID,
    val principalKind: String,
    @Serializable(with = UUIDSerializer::class)
    val principalId: UUID,
    /** Best-effort human label (user email / group name); null if it can't be resolved. */
    val displayName: String? = null,
    val roleName: ExchangeShareRoleName,
    val source: String,
    val status: String,
    val recipientPurpose: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val grantedByAppUserId: UUID? = null,
    @Serializable(with = TimestampSerializer::class)
    val grantedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val expiresAt: Timestamp? = null,
    val constraintsJson: String? = null,
)
