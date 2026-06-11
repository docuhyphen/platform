package com.docuhyphen.app.api.service.auth

import java.util.UUID

data class StoredRefreshToken(
    val userId: UUID,
    val token: String,
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
        refreshToken: String,
        refreshTokenHash: String,
        expirySeconds: Long,
        sessionId: UUID? = null,
    )

    fun rotate(
        userId: UUID,
        currentJti: String,
        currentTokenHash: String,
        newJti: String,
        newToken: String,
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

