package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.core.NewCookie
import org.slf4j.LoggerFactory

data class TokenTriple(
    val accessToken: String,
    val idToken: String,
    val refreshToken: String,
    val refreshTokenJti: String,
)

@RequestScoped
class TokenIssuanceService @Inject constructor(
    private val authenticationService: AuthenticationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(TokenIssuanceService::class.java)
    }

    @Transactional
    fun issueTokenTriple(appUser: AppUser): TokenTriple
    {
        val accessToken = authenticationService.generateAccessToken(appUser)
        val idToken = authenticationService.generateIdToken(appUser)
        val (refreshToken, jti) = authenticationService.generateRefreshToken(appUser)

        authenticationService.saveRefreshToken(appUser, refreshToken, jti)

        logger.info("Issued token triple for user {}", appUser.id)
        return TokenTriple(accessToken, idToken, refreshToken, jti)
    }

    fun buildRefreshTokenCookie(refreshToken: String, secure: Boolean = false): NewCookie
    {
        return NewCookie.Builder("refresh_token")
            .value(refreshToken)
            .path("/auth/token")
            .httpOnly(true)
            .secure(secure)
            .sameSite(NewCookie.SameSite.STRICT)
            .maxAge(7 * 24 * 60 * 60)
            .build()
    }

    fun buildClearRefreshTokenCookie(): NewCookie
    {
        return NewCookie.Builder("refresh_token")
            .value("")
            .path("/auth/token")
            .httpOnly(true)
            .secure(false)
            .sameSite(NewCookie.SameSite.STRICT)
            .maxAge(0)
            .build()
    }
}

