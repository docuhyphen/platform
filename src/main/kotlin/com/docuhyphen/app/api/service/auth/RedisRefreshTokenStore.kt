package com.docuhyphen.app.api.service.auth

import io.vertx.mutiny.redis.client.Command
import io.vertx.mutiny.redis.client.Redis
import io.vertx.mutiny.redis.client.Request
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID

@ApplicationScoped
class RedisRefreshTokenStore @Inject constructor(
    private val redis: Redis,
) : RefreshTokenStore
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RedisRefreshTokenStore::class.java)

        private const val TOKEN_KEY_PREFIX = "refresh_token:"
        private const val USER_TOKENS_KEY_PREFIX = "user_refresh_tokens:"
    }

    override fun save(userId: UUID, jti: String, refreshToken: String, expirySeconds: Long)
    {
        val tokenKey = "$TOKEN_KEY_PREFIX$jti"
        val userTokensKey = "$USER_TOKENS_KEY_PREFIX$userId"

        // Store token data as a hash
        redis.send(
            Request.cmd(Command.HSET)
                .arg(tokenKey)
                .arg("userId").arg(userId.toString())
                .arg("token").arg(refreshToken)
                .arg("jti").arg(jti)
        ).await().indefinitely()

        // Set TTL
        redis.send(
            Request.cmd(Command.EXPIRE)
                .arg(tokenKey)
                .arg(expirySeconds.toString())
        ).await().indefinitely()

        // Track jti under user's set
        redis.send(
            Request.cmd(Command.SADD)
                .arg(userTokensKey)
                .arg(jti)
        ).await().indefinitely()

        redis.send(
            Request.cmd(Command.EXPIRE)
                .arg(userTokensKey)
                .arg(expirySeconds.toString())
        ).await().indefinitely()

        logger.debug("Saved refresh token jti={} for userId={}", jti, userId)
    }

    override fun findByJti(jti: String): StoredRefreshToken?
    {
        val tokenKey = "$TOKEN_KEY_PREFIX$jti"

        val response = redis.send(
            Request.cmd(Command.HGETALL).arg(tokenKey)
        ).await().indefinitely() ?: return null

        val keys = response.getKeys() ?: return null
        if (keys.isEmpty()) return null

        val data = mutableMapOf<String, String>()

        for (key in keys)
        {
            data[key] = response.get(key)?.toString() ?: ""
        }

        if (data.isEmpty()) return null

        return StoredRefreshToken(
            userId = UUID.fromString(data["userId"]),
            token = data["token"] ?: return null,
            jti = data["jti"] ?: return null,
        )
    }

    override fun deleteByJti(jti: String)
    {
        val tokenKey = "$TOKEN_KEY_PREFIX$jti"

        // Get userId before deleting so we can clean up the user set
        val response = redis.send(
            Request.cmd(Command.HGET).arg(tokenKey).arg("userId")
        ).await().indefinitely()

        val userId = response?.toString()

        redis.send(
            Request.cmd(Command.DEL).arg(tokenKey)
        ).await().indefinitely()

        if (userId != null)
        {
            redis.send(
                Request.cmd(Command.SREM)
                    .arg("$USER_TOKENS_KEY_PREFIX$userId")
                    .arg(jti)
            ).await().indefinitely()
        }

        logger.debug("Deleted refresh token jti={}", jti)
    }

    override fun deleteAllByUserId(userId: UUID)
    {
        val userTokensKey = "$USER_TOKENS_KEY_PREFIX$userId"

        val response = redis.send(
            Request.cmd(Command.SMEMBERS).arg(userTokensKey)
        ).await().indefinitely()

        val jtis = mutableListOf<String>()

        if (response != null)
        {
            for (i in 0 until response.size())
            {
                response.get(i)?.toString()?.let { jtis.add(it) }
            }
        }

        jtis.forEach { jti ->
            redis.send(
                Request.cmd(Command.DEL).arg("$TOKEN_KEY_PREFIX$jti")
            ).await().indefinitely()
        }

        redis.send(
            Request.cmd(Command.DEL).arg(userTokensKey)
        ).await().indefinitely()

        logger.debug("Deleted all refresh tokens for userId={} (count={})", userId, jtis.size)
    }
}

