package com.docuhyphen.app.api.service.informationrequest.noauth

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
    private val limits: InformationRequestAbuseLimits,
)
{
    fun requireWithinLimit(attempt: InformationRequestNoAuthAttempt, clientAddress: String?)
    {
        val address = clientAddress ?: ClientIpResolver.UNKNOWN_IP
        val (budget, control) = when (attempt)
        {
            InformationRequestNoAuthAttempt.CHALLENGE ->
                limits.noAuthChallengesPerMinute to InformationRequestAbuseControl.NO_AUTH_CHALLENGE_RATE

            InformationRequestNoAuthAttempt.SESSION ->
                limits.noAuthSessionsPerMinute to InformationRequestAbuseControl.NO_AUTH_SESSION_RATE
        }
        val retryAfterSeconds = rateLimitService.retryAfterSeconds(
            "information-request:no-auth:${attempt.name.lowercase()}:$address",
            budget,
        ) ?: return
        InformationRequestAbuseLog.refused(control)
        throw InformationRequestRateLimitedException(
            InformationRequestErrorCatalog.RATE_LIMITED,
            retryAfterSeconds,
            "Too many attempts from this address. Try again later.",
        )
    }
}
