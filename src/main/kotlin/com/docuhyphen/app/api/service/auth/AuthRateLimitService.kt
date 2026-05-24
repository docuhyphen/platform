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
}


