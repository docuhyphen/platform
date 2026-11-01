package com.docuhyphen.app.api.model.auth

import java.util.UUID

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
