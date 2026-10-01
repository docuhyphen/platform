package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseControl
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestNoAuthAttempt
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.ClientIpResolver
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestNoAuthRateLimit @Inject constructor(
    private val rateLimitService: AuthRateLimitService,
    private val requestContext: AuthTokenContext,
    private val limits: InformationRequestAbuseLimits,
)
{
    fun requireWithinLimit(attempt: InformationRequestNoAuthAttempt)
    {
        val address = requestContext.clientIp ?: ClientIpResolver.UNKNOWN_IP
        val (budget, control) = when (attempt)
        {
            InformationRequestNoAuthAttempt.CHALLENGE ->
                limits.noAuthChallengesPerMinute to InformationRequestAbuseControl.NO_AUTH_CHALLENGE_RATE
            InformationRequestNoAuthAttempt.SESSION ->
                limits.noAuthSessionsPerMinute to InformationRequestAbuseControl.NO_AUTH_SESSION_RATE
        }
        if (rateLimitService.isLimited("information-request:no-auth:${attempt.name.lowercase()}:$address", budget))
        {
            InformationRequestAbuseLog.refused(control)
            throw InformationRequestRateLimitedException(
                InformationRequestErrorCatalog.RATE_LIMITED,
                RETRY_AFTER_SECONDS,
                "Too many attempts from this address. Try again later.",
            )
        }
    }

    private companion object
    {
        const val RETRY_AFTER_SECONDS = 60L
    }
}
