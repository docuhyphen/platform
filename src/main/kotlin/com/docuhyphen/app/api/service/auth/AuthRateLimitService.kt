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

    /**
     * Returns how many distinct [member] values have been recorded against [key] in the current
     * window, after recording this one. Returns 0 when throttling is disabled so callers that
     * compare against a budget stay inert.
     */
    fun countDistinct(key: String, member: String, windowSeconds: Long): Long
    {
        if (!configurationService.isAuthRateLimitEnabled())
        {
            return 0
        }

        return redisRateLimiter.countDistinctMembers(key, member, windowSeconds)
    }
}


