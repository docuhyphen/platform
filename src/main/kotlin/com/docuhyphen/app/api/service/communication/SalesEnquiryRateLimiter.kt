package com.docuhyphen.app.api.service.communication

import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.ConcurrentHashMap

/**
 * Best-effort, in-memory throttle for the public sales-enquiry intake. It keeps a short sliding
 * window of recent submission timestamps per source key (client IP) and rejects a source that
 * exceeds the allowed number of submissions within the window. State is per-instance and
 * intentionally lightweight: it is a first line of defence against casual abuse, not a
 * distributed guarantee.
 */
@ApplicationScoped
class SalesEnquiryRateLimiter
{
    private val windowMillis = 10 * 60 * 1000L
    private val maxSubmissionsPerWindow = 5

    private val submissionsBySource = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * Records an attempt for [sourceKey] and returns the number of seconds the caller must wait
     * before another attempt is permitted, or null when the attempt is within the allowed rate.
     */
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

