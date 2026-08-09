package com.docuhyphen.app.api.service.auth

import io.vertx.mutiny.redis.client.Command
import io.vertx.mutiny.redis.client.Redis
import io.vertx.mutiny.redis.client.Request
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class RedisRateLimiter @Inject constructor(
    private val redis: Redis,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RedisRateLimiter::class.java)

        private const val RATE_LIMIT_PREFIX = "rate_limit:"
        private const val BLOCK_PREFIX = "rate_limit_block:"
        private const val VIOLATION_PREFIX = "rate_limit_violation:"
        private const val DISTINCT_PREFIX = "rate_limit_distinct:"
    }

    /**
     * Records [member] against [key] and returns how many distinct members have been seen in the
     * current window. Used to spot enumeration, where each individual request is within budget
     * but the spread of subjects being probed is not something a real user produces.
     */
    fun countDistinctMembers(key: String, member: String, windowSeconds: Long): Long
    {
        val redisKey = "$DISTINCT_PREFIX$key"

        val added = redis.send(
            Request.cmd(Command.SADD).arg(redisKey).arg(member)
        ).await().indefinitely()?.toLong() ?: 0L

        if (added == 1L)
        {
            redis.send(
                Request.cmd(Command.EXPIRE)
                    .arg(redisKey)
                    .arg(windowSeconds.toString())
                    .arg("NX")
            ).await().indefinitely()
        }

        return redis.send(
            Request.cmd(Command.SCARD).arg(redisKey)
        ).await().indefinitely()?.toLong() ?: 0L
    }

    fun isRateLimited(key: String, maxRequests: Long, windowSeconds: Long): Boolean
    {
        val redisKey = "$RATE_LIMIT_PREFIX$key"

        val current = redis.send(
            Request.cmd(Command.INCR).arg(redisKey)
        ).await().indefinitely()?.toLong() ?: 0L

        if (current == 1L)
        {
            redis.send(
                Request.cmd(Command.EXPIRE)
                    .arg(redisKey)
                    .arg(windowSeconds.toString())
            ).await().indefinitely()
        }

        if (current > maxRequests)
        {
            logger.warn("Rate limit exceeded for key={} (count={}, max={})", key, current, maxRequests)
            return true
        }

        return false
    }

    fun isRateLimitedWithBackoff(key: String, maxRequests: Long, windowSeconds: Long): Boolean
    {
        if (isTemporarilyBlocked(key))
        {
            logger.warn("Temporary rate-limit block active for key={}", key)
            return true
        }

        if (!isRateLimited(key, maxRequests, windowSeconds))
        {
            return false
        }

        val blockSeconds = registerViolationAndGetBlockSeconds(key)
        applyBlock(key, blockSeconds)
        logger.warn("Progressive backoff applied for key={} blockSeconds={}", key, blockSeconds)
        return true
    }

    private fun isTemporarilyBlocked(key: String): Boolean
    {
        val blockKey = "$BLOCK_PREFIX$key"
        val exists = redis.send(
            Request.cmd(Command.EXISTS).arg(blockKey)
        ).await().indefinitely()?.toLong() ?: 0L
        return exists > 0
    }

    private fun registerViolationAndGetBlockSeconds(key: String): Long
    {
        val violationKey = "$VIOLATION_PREFIX$key"
        val count = redis.send(
            Request.cmd(Command.INCR).arg(violationKey)
        ).await().indefinitely()?.toLong() ?: 1L

        if (count == 1L)
        {
            // Keep strike history for 24h before auto decay.
            redis.send(
                Request.cmd(Command.EXPIRE)
                    .arg(violationKey)
                    .arg("86400")
            ).await().indefinitely()
        }

        return when
        {
            count <= 2L -> 60L
            count <= 4L -> 300L
            count <= 6L -> 900L
            else -> 3600L
        }
    }

    private fun applyBlock(key: String, seconds: Long)
    {
        val blockKey = "$BLOCK_PREFIX$key"
        redis.send(
            Request.cmd(Command.SET)
                .arg(blockKey)
                .arg("1")
                .arg("EX")
                .arg(seconds.toString())
        ).await().indefinitely()
    }
}
