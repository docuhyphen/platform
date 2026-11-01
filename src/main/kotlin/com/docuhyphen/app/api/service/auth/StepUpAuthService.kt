package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@RequestScoped
class StepUpAuthService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val authenticationService: AuthenticationService,
    private val userSessionService: UserSessionService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(StepUpAuthService::class.java)
        private const val DEFAULT_MAX_AGE_SECONDS = 300L
    }

    fun isFresh(maxAgeSeconds: Long = DEFAULT_MAX_AGE_SECONDS): Boolean
    {
        val claims =
            authenticationService.parseTokenClaims((authTokenContext.authToken.token ?: return false)) ?: return false
        val nowSeconds = System.currentTimeMillis() / 1000

        val tokenAuthTime = (claims["auth_time"] as? Number)?.toLong()

        val sessionAuthTime = ((claims["exchange_id"] as? String)
            ?.let { raw -> runCatching { java.util.UUID.fromString(raw) }.getOrNull() }
            ?.let { sessionId -> userSessionService.findSession(sessionId)?.lastAuthTime?.toInstant()?.epochSecond })

        val authTime = listOfNotNull(tokenAuthTime, sessionAuthTime).maxOrNull() ?: return false
        return (nowSeconds - authTime) <= maxAgeSeconds
    }

    fun isFresh(sessionId: java.util.UUID, maxAgeSeconds: Long = DEFAULT_MAX_AGE_SECONDS): Boolean
    {
        val authTime = userSessionService.findSession(sessionId)?.lastAuthTime?.toInstant()?.epochSecond
            ?: return false
        return (System.currentTimeMillis() / 1000 - authTime) <= maxAgeSeconds
    }

    fun markFresh(sessionId: java.util.UUID)
    {
        userSessionService.markFreshAuth(sessionId)
        logger.info("Marked fresh auth for session={}", sessionId)
    }

    fun markFresh()
    {
        val token = authTokenContext.authToken.token ?: return
        val sessionIdRaw = authenticationService.parseTokenClaims(token)
            ?.get("exchange_id") as? String ?: return
        val sessionId = runCatching { java.util.UUID.fromString(sessionIdRaw) }.getOrNull() ?: return
        markFresh(sessionId)
    }
}
