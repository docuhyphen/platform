package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Step-up authentication for sensitive endpoints.
 *
 * Reads the `auth_time` claim from the current access token and decides whether the user
 * has authenticated recently enough for a high-value action. If not, callers should reject
 * the request with 401 + reasonCode = STEP_UP_REQUIRED so the client can trigger a re-auth.
 *
 * Default freshness window: 5 minutes. Tunable per call.
 */
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

    /**
     * Returns true if the current request's access token was minted from a recent authentication.
     * "Recent" means [maxAgeSeconds] (default 5 minutes) since the user last passed a real auth challenge.
     */
    fun isFresh(maxAgeSeconds: Long = DEFAULT_MAX_AGE_SECONDS): Boolean
    {
        val claims = authenticationService.parseTokenClaims((authTokenContext.authToken.token ?: return false)) ?: return false
        val authTime = (claims["auth_time"] as? Number)?.toLong() ?: return false
        val nowSeconds = System.currentTimeMillis() / 1000
        return (nowSeconds - authTime) <= maxAgeSeconds
    }

    /**
     * Marks the current session as having just passed a fresh-auth challenge.
     * Callers should invoke this after re-verifying a password / MFA factor.
     */
    fun markFresh()
    {
        val token = authTokenContext.authToken.token ?: return
        val sessionIdRaw = authenticationService.parseTokenClaims(token)
            ?.get("session_id") as? String ?: return
        val sessionId = runCatching { java.util.UUID.fromString(sessionIdRaw) }.getOrNull() ?: return
        userSessionService.markFreshAuth(sessionId)
        logger.info("Marked fresh auth for session={}", sessionId)
    }
}
