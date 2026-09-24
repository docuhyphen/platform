package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.CurrentSessionDto
import com.docuhyphen.app.api.realtime.RealtimeSessionDeadlineService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class SessionActivityService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userSessionService: UserSessionService,
    private val sessionService: SessionService,
    private val realtimeSessionDeadlineService: RealtimeSessionDeadlineService,
)
{
    companion object
    {
        private const val MINIMUM_ACTIVITY_INTERVAL_SECONDS = 10L
    }

    fun recordActivity(): CurrentSessionDto
    {
        val sessionId = authTokenContext.userSessionId ?: throw UnauthorizedException("Session is unavailable")
        val appUserId = authTokenContext.authToken.appUser?.id ?: throw UnauthorizedException("User is unavailable")
        val endReason = userSessionService.accessEndReason(sessionId, appUserId)
        if (endReason != null) throw UnauthorizedException("Session is no longer active")
        userSessionService.recordActivity(sessionId, MINIMUM_ACTIVITY_INTERVAL_SECONDS)
        userSessionService.deadlines(sessionId)?.let { realtimeSessionDeadlineService.reschedule(sessionId, it) }
        return sessionService.currentSession()
    }
}
