package com.docuhyphen.app.api.service.auth

import java.util.UUID

/**
 * Server-side record of an issued refresh token.
 *
 * Only the SHA-256 hash of the bearer secret is ever retained. The raw token exists solely in
 * the response cookie, so read access to the token store cannot be turned into session takeover.
 */
data class StoredRefreshToken(
    val userId: UUID,
    val tokenHash: String,
    val jti: String,
    val familyId: String,
    val status: String,
    val sessionId: UUID? = null,
    val successorJti: String? = null,
    val graceUntilEpochMillis: Long? = null,
)

enum class RefreshRotationStatus
{
    ROTATED,
    GRACE_REPLAY,
    REUSE_DETECTED,
    NOT_FOUND,
    INVALID,
    REVOKED,
}

data class RefreshRotationResult(
    val status: RefreshRotationStatus,
    val familyId: String? = null,
    val successorJti: String? = null,
    val successorToken: String? = null,
)

interface RefreshTokenStore
{
    fun save(
        userId: UUID,
        jti: String,
        familyId: String,
        refreshTokenHash: String,
        expirySeconds: Long,
        sessionId: UUID? = null,
    )

    /**
     * Atomically consumes [currentJti] and installs [newJti] in the same family.
     *
     * The new token's raw value is never passed in or stored: the caller already holds it, and
     * the store only needs its hash to verify the next presentation.
     */
    fun rotate(
        userId: UUID,
        currentJti: String,
        currentTokenHash: String,
        newJti: String,
        newTokenHash: String,
        familyId: String,
        graceSeconds: Long,
        expirySeconds: Long,
        nowEpochMillis: Long,
    ): RefreshRotationResult

    fun findByJti(jti: String): StoredRefreshToken?

    fun deleteByJti(jti: String)

    fun deleteAllByUserId(userId: UUID)

    fun revokeFamily(familyId: String, reasonCode: RevocationReasonCode)
}
