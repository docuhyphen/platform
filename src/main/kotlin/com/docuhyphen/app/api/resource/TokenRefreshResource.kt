package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.entity.AuthTokenType.REFRESH
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.TokenRefreshResponse
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.TokenIssuanceService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Cookie
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

@Path("/auth/token")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class TokenRefreshResource @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val tokenIssuanceService: TokenIssuanceService,
    private val appUserService: AppUserService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(TokenRefreshResource::class.java)
    }

    @POST
    @Path("/refresh")
    fun refreshToken(
        @CookieParam("refresh_token") refreshTokenCookie: Cookie?,
    ): Response
    {
        return try
        {
            val refreshTokenValue = refreshTokenCookie?.value
            if (refreshTokenValue.isNullOrBlank())
            {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("No refresh token provided"))
                    .build()
            }

            val claims = authenticationService.parseTokenClaims(refreshTokenValue)
                ?: return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid or expired refresh token"))
                    .build()

            val tokenType = claims["token_type"] as? String
            if (tokenType != REFRESH.name)
            {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid token type"))
                    .build()
            }

            val jti = claims.id
            if (jti.isNullOrBlank())
            {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid refresh token"))
                    .build()
            }

            // Verify refresh token exists in Redis (not revoked)
            val storedToken = authenticationService.findRefreshTokenByJti(jti)
            if (storedToken == null)
            {
                logger.warn("Refresh token not found in store (possibly revoked): jti=$jti")
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Refresh token has been revoked"))
                    .build()
            }

            val userId = UUID.fromString(claims.subject)
            val appUser = appUserService.getById(userId)
                ?: return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("User not found"))
                    .build()

            // Rotate: delete old refresh token, issue new triple
            authenticationService.deleteRefreshTokenByJti(jti)

            val tokenTriple = tokenIssuanceService.issueTokenTriple(appUser)
            val cookie = tokenIssuanceService.buildRefreshTokenCookie(tokenTriple.refreshToken)

            Response.ok(TokenRefreshResponse(tokenTriple.accessToken, tokenTriple.idToken))
                .cookie(cookie)
                .build()
        }
        catch (e: Exception)
        {
            logger.error("Error refreshing token", e)

            Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Failed to refresh token"))
                .build()
        }
    }
}
