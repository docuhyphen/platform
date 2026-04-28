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
}
