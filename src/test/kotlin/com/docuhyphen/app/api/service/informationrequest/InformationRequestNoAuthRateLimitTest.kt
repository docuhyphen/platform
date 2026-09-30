package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.InformationRequestNoAuthAttempt
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InformationRequestNoAuthRateLimitTest
{
    private val rateLimitService = mock<AuthRateLimitService>()
    private val context = AuthTokenContext().apply { clientIp = "203.0.113.7" }
    private val rateLimit = InformationRequestNoAuthRateLimit(
        rateLimitService,
        context,
        InformationRequestAbuseLimits(noAuthChallengesPerMinute = 3, noAuthSessionsPerMinute = 5),
    )

    @Test
    fun `each client address has its own budget of contact-code challenges and of sessions`()
    {
        rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.CHALLENGE)
        rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.SESSION)

        verify(rateLimitService).isLimited("information-request:no-auth:challenge:203.0.113.7", 3)
        verify(rateLimitService).isLimited("information-request:no-auth:session:203.0.113.7", 5)
    }

    @Test
    fun `an address over its budget is refused with a time to retry`()
    {
        whenever(rateLimitService.isLimited(any(), any())).thenReturn(true)

        val refusal = assertThrows<InformationRequestRateLimitedException> {
            rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.SESSION)
        }

        assertEquals(InformationRequestErrorCatalog.RATE_LIMITED, refusal.reasonCode)
        assertEquals(60L, refusal.retryAfterSeconds)
    }
}
