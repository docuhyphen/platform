package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
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
    private val configurationService: ConfigurationService,
) : RefreshTokenStore
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RedisRefreshTokenStore::class.java)

        private const val TOKEN_KEY_PREFIX = "refresh_token:"
        private const val USER_TOKENS_KEY_PREFIX = "user_refresh_tokens:"
        private const val FAMILY_TOKENS_KEY_PREFIX = "refresh_token_family:"

        private const val LUA_ROTATE = """
            local currentKey = KEYS[1]
            local newKey = KEYS[2]
            local userSetKey = KEYS[3]
            local familySetKey = KEYS[4]

            local expectedUserId = ARGV[1]
            local expectedTokenHash = ARGV[2]
            local newJti = ARGV[3]
            local newTokenHash = ARGV[4]
            local familyId = ARGV[5]
            local nowMillis = tonumber(ARGV[6])
            local graceSeconds = tonumber(ARGV[7])
            local expirySeconds = tonumber(ARGV[8])
            local strictReuse = ARGV[9]

            if redis.call('EXISTS', currentKey) == 0 then
              return {'NOT_FOUND'}
            end

            local userId = redis.call('HGET', currentKey, 'userId')
            local tokenHash = redis.call('HGET', currentKey, 'tokenHash')
            local status = redis.call('HGET', currentKey, 'status')
            local currentFamilyId = redis.call('HGET', currentKey, 'familyId')
            local sessionId = redis.call('HGET', currentKey, 'sessionId')

            if userId ~= expectedUserId or tokenHash ~= expectedTokenHash or currentFamilyId ~= familyId then
              return {'INVALID'}
            end

            if status == 'REVOKED' then
              return {'REVOKED', familyId}
            end

            local function install()
              redis.call('HSET', newKey,
                  'userId', expectedUserId,
                  'tokenHash', newTokenHash,
                  'jti', newJti,
                  'familyId', familyId,
                  'status', 'ACTIVE')
              if sessionId and sessionId ~= '' then
                redis.call('HSET', newKey, 'sessionId', sessionId)
              end
              redis.call('EXPIRE', newKey, expirySeconds)
              redis.call('SADD', userSetKey, newJti)
              redis.call('EXPIRE', userSetKey, expirySeconds)
              redis.call('SADD', familySetKey, newJti)
              redis.call('EXPIRE', familySetKey, expirySeconds)
            end

            if status == 'CONSUMED' then
              local graceUntil = tonumber(redis.call('HGET', currentKey, 'graceUntil') or '0')
              local replayCount = tonumber(redis.call('HGET', currentKey, 'replayCount') or '0')

              -- Strict mode: any consumed-token presentation is reuse.
              -- Lenient mode: tolerate exactly one grace replay (parallel tabs racing a refresh);
              -- a second replay is reuse. The replay is answered with a freshly minted token in
              -- the same family rather than a copy of the first successor, because the successor's
              -- raw value is deliberately not retained anywhere on the server.
              if strictReuse == 'true' then
                return {'REUSE_DETECTED', familyId}
              end

              if graceUntil >= nowMillis and replayCount < 1 then
                redis.call('HSET', currentKey, 'replayCount', tostring(replayCount + 1))
                install()
                return {'GRACE_REPLAY', familyId, newJti}
              end
              return {'REUSE_DETECTED', familyId}
            end

            if status ~= 'ACTIVE' then
              return {'INVALID'}
            end

            local graceUntil = nowMillis + (graceSeconds * 1000)
            redis.call('HSET', currentKey,
                'status', 'CONSUMED',
                'consumedAt', tostring(nowMillis),
                'graceUntil', tostring(graceUntil),
                'successorJti', newJti)

            install()

            return {'ROTATED', familyId, newJti}
        """

        private const val LUA_REVOKE_FAMILY = """
            local familySetKey = KEYS[1]
            local reasonCode = ARGV[2]
            local jtis = redis.call('SMEMBERS', familySetKey)
            for _, jti in ipairs(jtis) do
              local tokenKey = 'refresh_token:' .. jti
              if redis.call('EXISTS', tokenKey) == 1 then
                redis.call('HSET', tokenKey, 'status', 'REVOKED')
                redis.call('HSET', tokenKey, 'revokedAt', ARGV[1])
                redis.call('HSET', tokenKey, 'revocationReasonCode', reasonCode)
              end
            end
            return jtis
        """
    }

    override fun save(
        userId: UUID,
        jti: String,
        familyId: String,
        refreshTokenHash: String,
        expirySeconds: Long,
        sessionId: UUID?,
    )
    {
        val tokenKey = "$TOKEN_KEY_PREFIX$jti"
        val userTokensKey = "$USER_TOKENS_KEY_PREFIX$userId"
        val familyTokensKey = "$FAMILY_TOKENS_KEY_PREFIX$familyId"

        // Only the hash is written. The raw token never leaves the response cookie.
        val req = Request.cmd(Command.HSET)
            .arg(tokenKey)
            .arg("userId").arg(userId.toString())
            .arg("tokenHash").arg(refreshTokenHash)
            .arg("jti").arg(jti)
            .arg("familyId").arg(familyId)
            .arg("status").arg("ACTIVE")
        if (sessionId != null) req.arg("sessionId").arg(sessionId.toString())
        redis.send(req).await().indefinitely()

        redis.send(
            Request.cmd(Command.EXPIRE)
                .arg(tokenKey)
                .arg(expirySeconds.toString())
        ).await().indefinitely()

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

        redis.send(
            Request.cmd(Command.SADD)
                .arg(familyTokensKey)
                .arg(jti)
        ).await().indefinitely()

        redis.send(
            Request.cmd(Command.EXPIRE)
                .arg(familyTokensKey)
                .arg(expirySeconds.toString())
        ).await().indefinitely()

        logger.debug("Saved refresh token for userId={}", userId)
    }

    override fun rotate(
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
    {
        val strictReuse = configurationService.isAuthRefreshStrictReuseDetectionEnabled()
        val response = redis.send(
            Request.cmd(Command.EVAL)
                .arg(LUA_ROTATE)
                .arg("4")
                .arg("$TOKEN_KEY_PREFIX$currentJti")
                .arg("$TOKEN_KEY_PREFIX$newJti")
                .arg("$USER_TOKENS_KEY_PREFIX$userId")
                .arg("$FAMILY_TOKENS_KEY_PREFIX$familyId")
                .arg(userId.toString())
                .arg(currentTokenHash)
                .arg(newJti)
                .arg(newTokenHash)
                .arg(familyId)
                .arg(nowEpochMillis.toString())
                .arg(graceSeconds.toString())
                .arg(expirySeconds.toString())
                .arg(strictReuse.toString())
        ).await().indefinitely()

        if (response == null || response.size() == 0)
        {
            return RefreshRotationResult(status = RefreshRotationStatus.INVALID)
        }

        val statusRaw = response.get(0)?.toString() ?: return RefreshRotationResult(RefreshRotationStatus.INVALID)
        val status = runCatching { RefreshRotationStatus.valueOf(statusRaw) }.getOrDefault(RefreshRotationStatus.INVALID)
        val resolvedFamilyId = if (response.size() > 1) response.get(1)?.toString() else null
        val successorJti = if (response.size() > 2) response.get(2)?.toString()?.ifBlank { null } else null

        return RefreshRotationResult(
            status = status,
            familyId = resolvedFamilyId,
            successorJti = successorJti,
        )
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
            tokenHash = data["tokenHash"] ?: return null,
            jti = data["jti"] ?: return null,
            familyId = data["familyId"] ?: return null,
            status = data["status"] ?: return null,
            sessionId = data["sessionId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            successorJti = data["successorJti"],
            graceUntilEpochMillis = data["graceUntil"]?.toLongOrNull(),
        )
    }

    override fun deleteByJti(jti: String)
    {
        val tokenKey = "$TOKEN_KEY_PREFIX$jti"

        val userIdResponse = redis.send(
            Request.cmd(Command.HGET).arg(tokenKey).arg("userId")
        ).await().indefinitely()
        val familyIdResponse = redis.send(
            Request.cmd(Command.HGET).arg(tokenKey).arg("familyId")
        ).await().indefinitely()

        val userId = userIdResponse?.toString()
        val familyId = familyIdResponse?.toString()

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

        if (familyId != null)
        {
            redis.send(
                Request.cmd(Command.SREM)
                    .arg("$FAMILY_TOKENS_KEY_PREFIX$familyId")
                    .arg(jti)
            ).await().indefinitely()
        }

        logger.debug("Deleted refresh token record")
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
            val familyId = redis.send(
                Request.cmd(Command.HGET)
                    .arg("$TOKEN_KEY_PREFIX$jti")
                    .arg("familyId")
            ).await().indefinitely()?.toString()

            redis.send(
                Request.cmd(Command.DEL).arg("$TOKEN_KEY_PREFIX$jti")
            ).await().indefinitely()

            if (!familyId.isNullOrBlank())
            {
                redis.send(
                    Request.cmd(Command.SREM)
                        .arg("$FAMILY_TOKENS_KEY_PREFIX$familyId")
                        .arg(jti)
                ).await().indefinitely()
            }
        }

        redis.send(
            Request.cmd(Command.DEL).arg(userTokensKey)
        ).await().indefinitely()

        logger.debug("Deleted all refresh tokens for userId={} (count={})", userId, jtis.size)
    }

    override fun revokeFamily(familyId: String, reasonCode: RevocationReasonCode)
    {
        redis.send(
            Request.cmd(Command.EVAL)
                .arg(LUA_REVOKE_FAMILY)
                .arg("1")
                .arg("$FAMILY_TOKENS_KEY_PREFIX$familyId")
                .arg(System.currentTimeMillis().toString())
                .arg(reasonCode.name)
        ).await().indefinitely()

        logger.warn("Revoked refresh token family reasonCode={}", reasonCode)
    }
}

