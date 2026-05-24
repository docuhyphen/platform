package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@RequestScoped
class SignOutService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val appUserRepository: AppUserRepository,
    private val authAuditService: AuthAuditService,
    private val authenticationService: AuthenticationService,
    private val userSessionService: UserSessionService,
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignOutService::class.java)
    }

    fun signOut(outOfAllDevices: Boolean = false, requestId: String? = null)
    {
        val appUser = authTokenContext.authToken.appUser!!

        if (outOfAllDevices)
        {
            logger.info("Signing out from all devices for user={}", appUser.id)
            authenticationService.deleteAllRefreshTokensForUser(appUser.id, RevocationReasonCode.LOGOUT_ALL_DEVICES)
            userSessionService.revokeAllUserSessions(appUser.id, RevocationReasonCode.LOGOUT_ALL_DEVICES)
            if (configurationService.isAuthSessionVersionEnabled())
            {
                // Force currently issued access tokens to fail request-time session_version checks.
                appUser.sessionVersion += 1
                appUserRepository.update(appUser)
            }
            authAuditService.emit(
                action = "SIGN_OUT",
                outcome = "SUCCESS",
                reasonCode = RevocationReasonCode.LOGOUT_ALL_DEVICES,
                actorId = appUser.id,
                requestId = requestId,
            )
            return
        }

        logger.info("Signing out from the current device for user={}", appUser.id)
        val claims = authenticationService.verifyAccessToken(authTokenContext.authToken.token)
        val sessionId = runCatching {
            java.util.UUID.fromString(claims?.get("session_id") as? String ?: "")
        }.getOrNull()

        sessionId?.let { userSessionService.revokeSession(it, RevocationReasonCode.LOGOUT_DEVICE) }

        authAuditService.emit(
            action = "SIGN_OUT",
            outcome = "SUCCESS",
            reasonCode = RevocationReasonCode.LOGOUT_DEVICE,
            actorId = appUser.id,
            sessionId = sessionId?.toString(),
            requestId = requestId,
        )
        logger.info("User signed out successfully.")
    }
}