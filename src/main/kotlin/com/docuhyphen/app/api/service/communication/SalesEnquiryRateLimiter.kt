package com.docuhyphen.app.api.service.communication

import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class SalesEnquiryRateLimiter
{
    private val windowMillis = 10 * 60 * 1000L
    private val maxSubmissionsPerWindow = 5

    private val submissionsBySource = ConcurrentHashMap<String, MutableList<Long>>()

    fun registerAndCheck(sourceKey: String, nowMillis: Long = System.currentTimeMillis()): Long?
    {
        val timestamps = submissionsBySource.computeIfAbsent(sourceKey) { mutableListOf() }

        synchronized(timestamps)
        {
            timestamps.removeAll { it <= nowMillis - windowMillis }

            if (timestamps.size >= maxSubmissionsPerWindow)
            {
                val oldest = timestamps.minOrNull() ?: nowMillis
                val retryAfterMillis = (oldest + windowMillis) - nowMillis
                return (retryAfterMillis / 1000).coerceAtLeast(1)
            }

            timestamps.add(nowMillis)
            return null
        }
    }
}

