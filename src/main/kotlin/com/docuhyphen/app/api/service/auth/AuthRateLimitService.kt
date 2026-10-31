package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class AuthRateLimitService @Inject constructor(
    private val redisRateLimiter: RedisRateLimiter,
    private val configurationService: ConfigurationService,
)
{
    fun isLimited(key: String, maxPerMinute: Long): Boolean
    {
        if (!configurationService.isAuthRateLimitEnabled())
        {
            return false
        }

        return redisRateLimiter.isRateLimitedWithBackoff(key, maxPerMinute, 60)
    }

    fun retryAfterSeconds(key: String, maxPerMinute: Long): Long? {
        if (!isLimited(key, maxPerMinute)) {
            return null
        }

        return redisRateLimiter.blockSecondsRemaining(key).coerceAtLeast(1)
    }

    fun countDistinct(key: String, member: String, windowSeconds: Long): Long
    {
        if (!configurationService.isAuthRateLimitEnabled())
        {
            return 0
        }

        return redisRateLimiter.countDistinctMembers(key, member, windowSeconds)
    }

    fun claimCooldown(key: String, seconds: Long): Long
    {
        if (!configurationService.isAuthRateLimitEnabled())
        {
            return 0
        }

        return redisRateLimiter.claimCooldown(key, seconds)
    }
}


