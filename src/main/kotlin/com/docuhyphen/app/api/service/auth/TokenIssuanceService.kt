package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.core.NewCookie
import org.eclipse.microprofile.config.ConfigProvider
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

data class TokenTriple(
    val accessToken: String,
    val idToken: String,
    val refreshToken: String,
    val refreshTokenJti: String,
)

@RequestScoped
class TokenIssuanceService @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val csrfProtectionService: CsrfProtectionService,
    private val userSessionService: UserSessionService,
    private val authSessionPolicyService: AuthSessionPolicyService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(TokenIssuanceService::class.java)

        internal fun shouldUseSecureCookies(baseUrl: String): Boolean =
            baseUrl.trim().startsWith("https://", ignoreCase = true)
    }

    @Transactional
    fun issueTokenTriple(appUser: AppUser, userAgent: String? = null, ipAddress: String? = null): TokenTriple
    {
        val policy = authSessionPolicyService.resolveForAppUser(appUser)
        val userSession = userSessionService.createSession(appUser, policy.maxSessionDurationHours, userAgent, ipAddress)
        val accessToken = authenticationService.generateAccessToken(
            appUser,
            userSession.sessionId,
            policy.accessTokenExpiryMinutes,
        )
        val idToken = authenticationService.generateIdToken(
            appUser,
            userSession.sessionId,
            policy.accessTokenExpiryMinutes,
        )
        val (refreshToken, jti, familyId) = authenticationService.generateRefreshToken(
            appUser,
            sessionId = userSession.sessionId,
            expiryMinutesOverride = policy.refreshTokenExpiryMinutes,
        )

        authenticationService.saveRefreshToken(
            appUser,
            refreshToken,
            jti,
            familyId,
            userSession.sessionId,
            policy.refreshTokenExpiryMinutes,
        )

        logger.info("Issued token triple for user {}", appUser.id)
        return TokenTriple(accessToken, idToken, refreshToken, jti)
    }

    fun buildRefreshTokenCookie(
        refreshToken: String,
        secure: Boolean = usesSecureCookies(),
        maxAgeSeconds: Int = (30 * 60),
    ): NewCookie
    {
        return NewCookie.Builder("refresh_token")
            .value(refreshToken)
            .path("/auth/token")
            .httpOnly(true)
            .secure(secure)
            .sameSite(NewCookie.SameSite.STRICT)
            .maxAge(maxAgeSeconds)
            .build()
    }

    fun buildRefreshTokenCookieWithPolicy(
        refreshToken: String,
        appUser: AppUser,
        secure: Boolean = usesSecureCookies(),
    ): NewCookie
    {
        val policy = authSessionPolicyService.resolveForAppUser(appUser)
        val maxAgeSeconds = TimeUnit.MINUTES.toSeconds(policy.refreshTokenExpiryMinutes).toInt().coerceAtLeast(1)
        return buildRefreshTokenCookie(refreshToken, secure, maxAgeSeconds)
    }

    fun buildClearRefreshTokenCookie(): NewCookie
    {
        return NewCookie.Builder("refresh_token")
            .value("")
            .path("/auth/token")
            .httpOnly(true)
            .secure(usesSecureCookies())
            .sameSite(NewCookie.SameSite.STRICT)
            .maxAge(0)
            .build()
    }

    fun buildCsrfTokenCookie(csrfToken: String, secure: Boolean = usesSecureCookies()): NewCookie
    {
        return NewCookie.Builder("csrf_token")
            .value(csrfToken)
            .path("/")
            .httpOnly(false)
            .secure(secure)
            .sameSite(NewCookie.SameSite.STRICT)
            .maxAge(7 * 24 * 60 * 60)
            .build()
    }

    fun buildClearCsrfTokenCookie(): NewCookie
    {
        return NewCookie.Builder("csrf_token")
            .value("")
            .path("/")
            .httpOnly(false)
            .secure(usesSecureCookies())
            .sameSite(NewCookie.SameSite.STRICT)
            .maxAge(0)
            .build()
    }

    fun generateCsrfToken(): String = csrfProtectionService.generateCsrfToken()

    private fun usesSecureCookies(): Boolean
    {
        val baseUrl = ConfigProvider.getConfig()
            .getOptionalValue("app.base-url", String::class.java)
            .orElse("")
        return shouldUseSecureCookies(baseUrl)
    }
}

