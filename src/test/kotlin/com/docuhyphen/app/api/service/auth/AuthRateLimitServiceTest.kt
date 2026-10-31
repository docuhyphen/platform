package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class AuthRateLimitServiceTest {
    private val limiter = mock<RedisRateLimiter>()
    private val configuration = mock<ConfigurationService>().also {
        whenever(it.isAuthRateLimitEnabled()).thenReturn(true)
    }
    private val service = AuthRateLimitService(limiter, configuration)

    @Test
    fun `a limited key answers the time left on its pause, however long the backoff has grown`() {
        whenever(limiter.isRateLimitedWithBackoff("process-key", 3, 60)).thenReturn(true)
        whenever(limiter.blockSecondsRemaining("process-key")).thenReturn(287L)

        assertEquals(287L, service.retryAfterSeconds("process-key", 3))
    }

    @Test
    fun `a key within its budget, or with limiting turned off, has nothing to wait for`() {
        whenever(limiter.isRateLimitedWithBackoff("process-key", 3, 60)).thenReturn(false)

        assertNull(service.retryAfterSeconds("process-key", 3))
        whenever(configuration.isAuthRateLimitEnabled()).thenReturn(false)
        assertNull(service.retryAfterSeconds("other-key", 3))
        verify(limiter, never()).blockSecondsRemaining(any())
    }

    @Test
    fun `a pause that ends as it is read still asks for at least a second`() {
        whenever(limiter.isRateLimitedWithBackoff("process-key", 3, 60)).thenReturn(true)
        whenever(limiter.blockSecondsRemaining("process-key")).thenReturn(0L)

        assertEquals(1L, service.retryAfterSeconds("process-key", 3))
    }
}
