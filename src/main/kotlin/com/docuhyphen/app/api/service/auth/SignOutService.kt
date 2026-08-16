package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID

@RequestScoped
class SignOutService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val appUserRepository: AppUserRepository,
    private val authAuditService: AuthAuditService,
    private val authenticationService: AuthenticationService,
    private val userSessionService: UserSessionService,
    private val configurationService: ConfigurationService,
    private val realtimeEventService: RealtimeEventService,
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
            val currentSessionId = currentSessionIdFromToken()

            // Notify peer devices BEFORE writing the revocation. Writing first caused a race:
            // once the session is in the Redis revocation cache, any in-flight request from a
            // peer device gets 401, the apiClient fires auth-session-expired, NotificationContext
            // cleanup runs disconnect(); the peer's socket is gone from the registry before
            // notifySessionRevoked can reach it. Sending first guarantees every socket is open.
            realtimeEventService.notifyAllSessionsRevoked(
                appUser.id,
                reason = RevocationReasonCode.LOGOUT_ALL_DEVICES.name,
                exceptUserSessionId = currentSessionId,
            )

            authenticationService.deleteAllRefreshTokensForUser(appUser.id, RevocationReasonCode.LOGOUT_ALL_DEVICES)
            // sendNotifications=false: we already notified above.
            userSessionService.revokeAllUserSessions(appUser.id, RevocationReasonCode.LOGOUT_ALL_DEVICES, sendNotifications = false)
            if (configurationService.isAuthSessionVersionEnabled())
            {
                // Force currently issued access tokens to fail request-time exchange_version checks.
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
        val sessionId = currentSessionIdFromToken()

        sessionId?.let {
            // notifyRevoked=false: the client initiated this sign-out and will navigate to /sign-in
            // itself. Sending EXCHANGE_REVOKED would race with that navigation and redirect the user
            // to /app-session-expired instead. The socket is still closed (with code 4001) so the
            // client stops reconnecting.
            userSessionService.revokeSession(it, RevocationReasonCode.LOGOUT_DEVICE, notifyRevoked = false)
            // Tell other tabs of the same user this device just signed out.
            realtimeEventService.notifySignedOutOtherDevice(appUser.id, exceptUserSessionId = it)
        }

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

    /**
     * Context-free sign-out used by flows that have no [AuthTokenContext],  e.g. password reset,
     * admin-driven revocation. Revokes all sessions for [appUserId] and pushes EXCHANGE_REVOKED
     * to every open socket.
     */
    fun signOutByUserId(appUserId: UUID, reason: RevocationReasonCode, requestId: String? = null)
    {
        logger.info("signOutByUserId user={} reason={}", appUserId, reason)
        // Notify all sockets BEFORE revocation for the same reason as signOut(outOfAllDevices=true).
        realtimeEventService.notifyAllSessionsRevoked(appUserId, reason.name)
        authenticationService.deleteAllRefreshTokensForUser(appUserId, reason)
        userSessionService.revokeAllUserSessions(appUserId, reason, sendNotifications = false)
        if (configurationService.isAuthSessionVersionEnabled())
        {
            val user = appUserRepository.findById(appUserId)
            if (user != null)
            {
                user.sessionVersion += 1
                appUserRepository.update(user)
            }
        }
        authAuditService.emit(
            action = "SIGN_OUT",
            outcome = "SUCCESS",
            reasonCode = reason,
            actorId = appUserId,
            requestId = requestId,
        )
    }

    private fun currentSessionIdFromToken(): UUID?
    {
        val claims = authenticationService.verifyAccessToken(authTokenContext.authToken.token) ?: return null
        return runCatching { UUID.fromString(claims["exchange_id"] as? String ?: "") }.getOrNull()
    }
}
