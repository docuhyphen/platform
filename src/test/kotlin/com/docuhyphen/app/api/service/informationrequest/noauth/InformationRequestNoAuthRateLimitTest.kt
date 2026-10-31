package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestNoAuthAttempt
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InformationRequestNoAuthRateLimitTest
{
    private val rateLimitService = mock<AuthRateLimitService>()
    private val rateLimit = InformationRequestNoAuthRateLimit(
        rateLimitService,
        InformationRequestAbuseLimits(noAuthChallengesPerMinute = 3, noAuthSessionsPerMinute = 5),
    )

    init {
        whenever(rateLimitService.retryAfterSeconds(any(), any())).thenReturn(null)
    }

    @Test
    fun `each client address has its own budget of contact-code challenges and of sessions`()
    {
        rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.CHALLENGE, "203.0.113.7")
        rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.SESSION, "203.0.113.7")

        verify(rateLimitService).retryAfterSeconds("information-request:no-auth:challenge:203.0.113.7", 3)
        verify(rateLimitService).retryAfterSeconds("information-request:no-auth:session:203.0.113.7", 5)
    }

    @Test
    fun `the limiter charges the address its caller passes and reads no request context`()
    {
        rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.CHALLENGE, "198.51.100.4")
        rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.SESSION, null)

        verify(rateLimitService).retryAfterSeconds("information-request:no-auth:challenge:198.51.100.4", 3)
        verify(rateLimitService).retryAfterSeconds("information-request:no-auth:session:0.0.0.0", 5)
        assertTrue(
            InformationRequestNoAuthRateLimit::class.java.declaredConstructors
                .flatMap { it.parameterTypes.toList() }
                .none { it == AuthTokenContext::class.java },
        )
    }

    @Test
    fun `an address over its budget is refused with the time left on its pause`() {
        whenever(rateLimitService.retryAfterSeconds(any(), any())).thenReturn(287L)

        val refusal = assertThrows<InformationRequestRateLimitedException> {
            rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.SESSION, "203.0.113.7")
        }

        assertEquals(InformationRequestErrorCatalog.RATE_LIMITED, refusal.reasonCode)
        assertEquals(287L, refusal.retryAfterSeconds)
    }
}
