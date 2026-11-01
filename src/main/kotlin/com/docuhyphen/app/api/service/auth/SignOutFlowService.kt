package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.auth.SessionCookies
import com.docuhyphen.app.api.model.auth.SignOutCommand
import com.docuhyphen.app.api.resource.model.LogoutPropagationInfoResponse
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class SignOutFlowService @Inject constructor(
    private val signOutService: SignOutService,
    private val tokenIssuanceService: TokenIssuanceService,
    private val csrfProtectionService: CsrfProtectionService,
    private val authRateLimitService: AuthRateLimitService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
)
{
    fun signOut(command: SignOutCommand): SessionCookies
    {
        if (authRateLimitService.isLimited(
                key = "auth:logout:${command.clientIp}",
                maxPerMinute = configurationService.getAuthRateLimitLogoutPerMinute(),
            )
        )
        {
            authAuditService.emit(
                action = "SIGN_OUT",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = command.requestId,
            )
            throw AuthRequestRateLimitedException()
        }

        if (!csrfProtectionService.verify(
                command.csrfCookie,
                command.csrfHeader,
                command.originHeader,
                command.refererHeader,
            )
        )
        {
            throw CsrfValidationFailedException()
        }

        signOutService.signOut(command.outOfAllDevices, command.requestId)
        return SessionCookies(
            refreshCookie = tokenIssuanceService.buildClearRefreshTokenCookie(),
            csrfCookie = tokenIssuanceService.buildClearCsrfTokenCookie(),
        )
    }

    fun propagationInfo(): LogoutPropagationInfoResponse = LogoutPropagationInfoResponse(
        platformLogoutAuthoritative = true,
        idpGlobalLogoutEquivalent = false,
        message = "Signing out from an identity provider is treated as an external signal and does not automatically revoke platform sessions.",
        exposureBounds = listOf(
            "Access tokens are short-lived by policy.",
            "Refresh requests enforce active user, organization membership, and session checks.",
            "Session/version revocation can invalidate all active device sessions.",
        ),
    )
}
