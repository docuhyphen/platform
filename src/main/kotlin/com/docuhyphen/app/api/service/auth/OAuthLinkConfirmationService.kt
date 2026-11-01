package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.auth.IssuedSessionTokens
import com.docuhyphen.app.api.model.auth.SessionCookies
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

sealed class OAuthLinkConfirmationException(message: String) : RuntimeException(message)

class LinkConfirmationRejectedException(message: String) : OAuthLinkConfirmationException(message)

class LinkTokenMalformedException(message: String) : OAuthLinkConfirmationException(message)

class LinkAccountNotFoundException(message: String) : OAuthLinkConfirmationException(message)

class LinkAccountNotEligibleException(message: String) : OAuthLinkConfirmationException(message)

@ApplicationScoped
class OAuthLinkConfirmationService @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val appUserService: AppUserService,
    private val oauthUserLinkingService: OAuthUserLinkingService,
    private val tokenIssuanceService: TokenIssuanceService,
    private val authAuditService: AuthAuditService,
)
{
    fun confirm(
        linkToken: String,
        password: String,
        requestId: String?,
        clientIp: String,
        userAgent: String?,
    ): IssuedSessionTokens
    {
        return try
        {
            completeConfirmation(linkToken, password, requestId, clientIp, userAgent)
        }
        catch (exception: OAuthLinkConfirmationException)
        {
            throw exception
        }
        catch (exception: Exception)
        {
            authAuditService.emit(
                action = "OAUTH_LINK_CONFIRM",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
            )
            throw exception
        }
    }

    private fun completeConfirmation(
        linkToken: String,
        password: String,
        requestId: String?,
        clientIp: String,
        userAgent: String?,
    ): IssuedSessionTokens
    {
        val claims = authenticationService.parseLinkTokenClaims(linkToken)
            ?: throw LinkConfirmationRejectedException("Invalid or expired link token")

        val tokenType = claims["token_type"] as? String
        if (tokenType != "LINK")
        {
            throw LinkConfirmationRejectedException("Invalid link token")
        }

        val email = claims.subject
        val providerName = claims["provider"] as? String
            ?: throw LinkTokenMalformedException("Invalid link token")
        val externalSubjectId = claims["externalSubjectId"] as? String
            ?: throw LinkTokenMalformedException("Invalid link token")

        val appUser = appUserService.findByEmail(email)
            ?: throw LinkAccountNotFoundException("User not found")

        if (!appUser.isActive || appUser.deprovisionedAt != null || appUser.isTemporary)
        {
            throw LinkAccountNotEligibleException("Account is not eligible for sign-in")
        }

        if (appUser.password == null || !authenticationService.validatePassword(password, appUser.password!!))
        {
            throw LinkConfirmationRejectedException("Invalid password")
        }

        val providerType = IdentityProviderType.valueOf(providerName)
        oauthUserLinkingService.createLink(appUser, providerType, externalSubjectId, email)

        val tokenTriple = tokenIssuanceService.issueTokenTriple(
            appUser,
            userAgent = userAgent,
            ipAddress = clientIp,
        )
        val refreshCookie =
            tokenIssuanceService.buildRefreshTokenCookieWithPolicy(tokenTriple.refreshToken, appUser)
        val csrfToken = tokenIssuanceService.generateCsrfToken()
        val csrfCookie = tokenIssuanceService.buildCsrfTokenCookie(csrfToken)

        authAuditService.emit(
            action = "OAUTH_LINK_CONFIRM",
            outcome = "SUCCESS",
            actorId = appUser.id,
            requestId = requestId,
        )
        return IssuedSessionTokens(
            accessToken = tokenTriple.accessToken,
            idToken = tokenTriple.idToken,
            cookies = SessionCookies(refreshCookie, csrfCookie),
        )
    }
}
