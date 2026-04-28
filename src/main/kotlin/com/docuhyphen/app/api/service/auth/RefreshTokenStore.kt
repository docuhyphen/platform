package com.docuhyphen.app.api.service.auth

import java.util.UUID

data class StoredRefreshToken(
    val userId: UUID,
    val token: String,
    val jti: String,
)

interface RefreshTokenStore
{
    fun save(userId: UUID, jti: String, refreshToken: String, expirySeconds: Long)

    fun findByJti(jti: String): StoredRefreshToken?

    fun deleteByJti(jti: String)

    fun deleteAllByUserId(userId: UUID)
}

