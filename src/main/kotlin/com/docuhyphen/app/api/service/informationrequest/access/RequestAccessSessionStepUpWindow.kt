package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration
import java.time.Instant

@ApplicationScoped
class RequestAccessSessionStepUpWindow @Inject constructor(
    @ConfigProperty(name = "app.information-request.access-session.step-up-window", defaultValue = "PT15M")
    private val window: Duration,
) {
    fun verifiedUntil(session: RequestAccessSession): Instant {
        val windowEnd = (session.steppedUpAt ?: session.issuedAt).toInstant().plus(window)
        val sessionEnd = session.expiresAt.toInstant() ?: return windowEnd
        return minOf(windowEnd, sessionEnd)
    }

    fun isVerified(session: RequestAccessSession, now: Instant): Boolean = verifiedUntil(session).isAfter(now)
}
