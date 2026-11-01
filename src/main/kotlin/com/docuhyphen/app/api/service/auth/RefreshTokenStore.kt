package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.auth.RefreshRotationResult
import com.docuhyphen.app.api.model.auth.StoredRefreshToken
import java.util.*

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
