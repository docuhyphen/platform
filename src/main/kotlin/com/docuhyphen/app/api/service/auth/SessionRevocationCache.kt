package com.docuhyphen.app.api.service.auth

import io.vertx.mutiny.redis.client.Command
import io.vertx.mutiny.redis.client.Redis
import io.vertx.mutiny.redis.client.Request
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * O(1) revocation check for session IDs. Replaces the coarser `sessionVersion` global bump:
 * EndpointAuthorizationFilter looks up the current request's exchange_id against this set on
 * every authenticated request, denying immediately if present.
 *
 * Entries TTL out after [DEFAULT_TTL_DAYS],  sessions older than that are guaranteed to have
 * expired naturally via the access-token / refresh-token expiry chain.
 */
@ApplicationScoped
class SessionRevocationCache @Inject constructor(
    private val redis: Redis,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SessionRevocationCache::class.java)
        private const val PREFIX = "revoked_session:"
        private const val DEFAULT_TTL_SECONDS = (30L * 24L * 60L * 60L) // 30 days
    }

    fun isRevoked(sessionId: UUID): Boolean
    {
        val response = redis.send(
            Request.cmd(Command.EXISTS).arg("$PREFIX$sessionId")
        ).await().indefinitely()
        return (response?.toLong() ?: 0L) > 0L
    }

    fun markRevoked(sessionId: UUID, reasonCode: RevocationReasonCode, ttlSeconds: Long = DEFAULT_TTL_SECONDS)
    {
        redis.send(
            Request.cmd(Command.SET)
                .arg("$PREFIX$sessionId")
                .arg(reasonCode.name)
                .arg("EX")
                .arg(ttlSeconds.toString())
        ).await().indefinitely()
        logger.info("Session marked revoked sessionId={} reasonCode={}", sessionId, reasonCode)
    }

    fun markManyRevoked(sessionIds: Collection<UUID>, reasonCode: RevocationReasonCode, ttlSeconds: Long = DEFAULT_TTL_SECONDS)
    {
        sessionIds.forEach { markRevoked(it, reasonCode, ttlSeconds) }
    }
}
