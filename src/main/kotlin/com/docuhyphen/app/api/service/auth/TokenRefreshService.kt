package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.auth.IssuedSessionTokens
import com.docuhyphen.app.api.model.auth.RefreshRotationStatus
import com.docuhyphen.app.api.model.auth.RiskLevel
import com.docuhyphen.app.api.model.auth.SessionCookies
import com.docuhyphen.app.api.model.auth.TokenRefreshCommand
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class TokenRefreshDeniedException(message: String, val reasonCode: RevocationReasonCode) : RuntimeException(message)

@ApplicationScoped
class TokenRefreshService @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val tokenIssuanceService: TokenIssuanceService,
    private val appUserService: AppUserService,
    private val authSessionPolicyService: AuthSessionPolicyService,
    private val organizationMembershipValidationService: OrganizationMembershipValidationService,
    private val csrfProtectionService: CsrfProtectionService,
    private val authAuditService: AuthAuditService,
    private val authRateLimitService: AuthRateLimitService,
    private val configurationService: ConfigurationService,
    private val userSessionService: UserSessionService,
    private val securityIncidentService: SecurityIncidentService,
    private val riskSignalService: RiskSignalService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(TokenRefreshService::class.java)
    }

    fun refresh(command: TokenRefreshCommand): IssuedSessionTokens
    {
        val clientIp = command.clientIp
        val requestId = command.requestId
        if (authRateLimitService.isLimited(
                key = "auth:refresh:$clientIp",
                maxPerMinute = configurationService.getAuthRateLimitRefreshPerMinute(),
            )
        )
        {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_REFRESH,
                severity = SecurityIncidentSeverity.MEDIUM,
                requestId = requestId,
                details = "ip=$clientIp",
            )
            authAuditService.emit(
                action = "TOKEN_REFRESH",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
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
            securityIncidentService.record(
                incidentType = SecurityIncidentType.CSRF_VALIDATION_FAILURE,
                severity = SecurityIncidentSeverity.HIGH,
                requestId = requestId,
                details = "origin=${command.originHeader};ip=$clientIp",
            )
            authAuditService.emit(
                action = "TOKEN_REFRESH",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.CSRF_VALIDATION_FAILED,
                requestId = requestId,
            )
            throw TokenRefreshDeniedException("CSRF validation failed", RevocationReasonCode.CSRF_VALIDATION_FAILED)
        }

        val refreshTokenValue = command.refreshToken
        if (refreshTokenValue.isNullOrBlank())
        {
            throw TokenRefreshDeniedException("No refresh token provided", RevocationReasonCode.REFRESH_INVALID)
        }

        val parts = refreshTokenValue.split('.', limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank())
        {
            throw TokenRefreshDeniedException("Invalid refresh token format", RevocationReasonCode.REFRESH_INVALID)
        }
        val jti = parts[0]
        val stored = authenticationService.findRefreshTokenByJti(jti)
            ?: throw TokenRefreshDeniedException(
                "Invalid or expired refresh token",
                RevocationReasonCode.REFRESH_INVALID,
            )

        if (!authenticationService.verifyRefreshTokenSecret(refreshTokenValue, stored))
        {
            throw TokenRefreshDeniedException("Invalid refresh token", RevocationReasonCode.REFRESH_INVALID)
        }

        val familyId = stored.familyId
        val sessionId = stored.sessionId
            ?: run {
                authenticationService.deleteRefreshTokenByJti(jti)
                logger.warn("Refresh token has no session association; forcing re-authentication")
                throw TokenRefreshDeniedException("Session is no longer active", RevocationReasonCode.REFRESH_INVALID)
            }

        val userId = stored.userId
        val appUser = appUserService.getById(userId)
            ?: throw TokenRefreshDeniedException("User not found", RevocationReasonCode.DEPROVISIONED)

        if (!appUser.isActive || appUser.deprovisionedAt != null)
        {
            authenticationService.deleteAllRefreshTokensForUser(appUser.id)
            authAuditService.emit(
                action = "TOKEN_REFRESH",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.DEPROVISIONED,
                actorId = appUser.id,
                requestId = requestId,
            )
            throw TokenRefreshDeniedException("User is inactive", RevocationReasonCode.DEPROVISIONED)
        }

        val membershipValidation = organizationMembershipValidationService.validateForSessionAccess(appUser)
        if (!membershipValidation.valid)
        {
            val reasonCode = membershipValidation.reasonCode ?: RevocationReasonCode.SECURITY_POLICY
            authenticationService.deleteAllRefreshTokensForUser(appUser.id, reasonCode)
            userSessionService.revokeAllUserSessions(appUser.id, reasonCode)
            authAuditService.emit(
                action = "TOKEN_REFRESH",
                outcome = "DENY",
                reasonCode = reasonCode,
                actorId = appUser.id,
                requestId = requestId,
                reason = membershipValidation.message,
            )
            throw TokenRefreshDeniedException(
                membershipValidation.message ?: "Organization membership is inactive",
                reasonCode,
            )
        }

        if (!userSessionService.isActiveSession(sessionId, appUser.id))
        {
            val reasonCode = userSessionService.endReason(sessionId) ?: RevocationReasonCode.REFRESH_INVALID
            authenticationService.deleteAllRefreshTokensForUser(appUser.id)
            authAuditService.emit(
                action = "TOKEN_REFRESH",
                outcome = "DENY",
                reasonCode = reasonCode,
                actorId = appUser.id,
                sessionId = sessionId.toString(),
                requestId = requestId,
            )
            throw TokenRefreshDeniedException("Session is no longer active", reasonCode)
        }

        val session = userSessionService.findSession(sessionId)
        if (session != null)
        {
            val risk = riskSignalService.evaluate(session, clientIp, command.userAgent, requestId)
            if (risk.level == RiskLevel.HIGH)
            {
                authenticationService.deleteAllRefreshTokensForUser(
                    appUser.id,
                    RevocationReasonCode.RISK_SIGNAL_DETECTED
                )
                userSessionService.revokeSession(sessionId, RevocationReasonCode.RISK_SIGNAL_DETECTED)
                authAuditService.emit(
                    action = "TOKEN_REFRESH",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.RISK_SIGNAL_DETECTED,
                    actorId = appUser.id,
                    sessionId = sessionId.toString(),
                    requestId = requestId,
                    reason = "Risk signals: ${risk.reasons.joinToString(",")}",
                )
                throw TokenRefreshDeniedException(
                    "Session terminated for security reasons.",
                    RevocationReasonCode.RISK_SIGNAL_DETECTED,
                )
            }
        }

        val policy = authSessionPolicyService.resolveForAppUser(appUser)

        val sessionForIdle = userSessionService.findSession(sessionId)
        val lastSeenInstant = sessionForIdle?.lastSeenAt?.toInstant()
        if (lastSeenInstant != null)
        {
            val idleSeconds = Duration.between(lastSeenInstant, Instant.now()).seconds
            val idleLimitSeconds = policy.idleTimeoutMinutes * 60
            if (idleSeconds > idleLimitSeconds)
            {
                authenticationService.deleteAllRefreshTokensForUser(
                    appUser.id,
                    RevocationReasonCode.INACTIVITY_TIMEOUT
                )
                userSessionService.revokeSession(sessionId, RevocationReasonCode.INACTIVITY_TIMEOUT)
                authAuditService.emit(
                    action = "TOKEN_REFRESH",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.INACTIVITY_TIMEOUT,
                    actorId = appUser.id,
                    sessionId = sessionId.toString(),
                    requestId = requestId,
                    reason = "Idle timeout exceeded (idleSeconds=$idleSeconds, limit=$idleLimitSeconds)",
                )
                throw TokenRefreshDeniedException(
                    "Session timed out due to inactivity",
                    RevocationReasonCode.INACTIVITY_TIMEOUT,
                )
            }
        }

        val rotationEnabled = configurationService.isAuthRefreshRotationEnabled()
        val reuseDetectionEnabled = configurationService.isAuthRefreshReuseDetectionEnabled()

        val rotation = if (rotationEnabled)
        {
            authenticationService.rotateRefreshToken(
                appUser = appUser,
                currentJti = jti,
                currentRefreshToken = refreshTokenValue,
                familyId = familyId,
                sessionId = sessionId,
                refreshExpiryMinutesOverride = policy.refreshTokenExpiryMinutes,
            )
        }
        else
        {
            null
        }

        if (rotationEnabled && reuseDetectionEnabled && rotation?.status == RefreshRotationStatus.REUSE_DETECTED)
        {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.REFRESH_TOKEN_REUSE_DETECTED,
                severity = SecurityIncidentSeverity.CRITICAL,
                actorId = appUser.id,
                requestId = requestId,
                details = "Refresh token replay detected for an active session",
            )
            rotation.familyId?.let {
                authenticationService.revokeRefreshFamily(it, RevocationReasonCode.REFRESH_REUSE_DETECTED)
            }
            authenticationService.deleteAllRefreshTokensForUser(appUser.id)
            userSessionService.revokeSession(sessionId, RevocationReasonCode.REFRESH_REUSE_DETECTED)
            logger.warn("Refresh token replay detected for user={}", appUser.id)
            authAuditService.emit(
                action = "TOKEN_REFRESH",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.REFRESH_REUSE_DETECTED,
                actorId = appUser.id,
                sessionId = sessionId.toString(),
                requestId = requestId,
            )
            throw TokenRefreshDeniedException(
                "Refresh token has been revoked",
                RevocationReasonCode.REFRESH_REUSE_DETECTED,
            )
        }

        if (rotationEnabled && rotation?.status != RefreshRotationStatus.ROTATED && rotation?.status != RefreshRotationStatus.GRACE_REPLAY)
        {
            authAuditService.emit(
                action = "TOKEN_REFRESH",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.REFRESH_INVALID,
                actorId = appUser.id,
                requestId = requestId,
            )
            throw TokenRefreshDeniedException("Invalid refresh token", RevocationReasonCode.REFRESH_INVALID)
        }

        val successorToken = if (rotationEnabled)
        {
            rotation?.successorToken
                ?: throw TokenRefreshDeniedException(
                    "Invalid refresh token state",
                    RevocationReasonCode.REFRESH_INVALID,
                )
        }
        else
        {
            val current = authenticationService.findRefreshTokenByJti(jti)
            if (current == null || current.userId != appUser.id || current.status.equals("REVOKED", ignoreCase = true))
            {
                authAuditService.emit(
                    action = "TOKEN_REFRESH",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.REFRESH_INVALID,
                    actorId = appUser.id,
                    requestId = requestId,
                )
                throw TokenRefreshDeniedException("Invalid refresh token", RevocationReasonCode.REFRESH_INVALID)
            }
            refreshTokenValue
        }

        val sessionAuthTimeEpoch = userSessionService.findSession(sessionId)?.lastAuthTime?.toInstant()?.epochSecond
        val accessToken = authenticationService.generateAccessToken(
            appUser = appUser,
            sessionId = sessionId,
            expiryMinutesOverride = policy.accessTokenExpiryMinutes,
            authTimeEpochSeconds = sessionAuthTimeEpoch,
        )
        val idToken = authenticationService.generateIdToken(appUser, sessionId, policy.accessTokenExpiryMinutes)
        val refreshCookie = tokenIssuanceService.buildRefreshTokenCookieWithPolicy(successorToken, appUser)
        val csrfToken = tokenIssuanceService.generateCsrfToken()
        val csrfTokenCookie = tokenIssuanceService.buildCsrfTokenCookie(csrfToken)
        authAuditService.emit(
            action = "TOKEN_REFRESH",
            outcome = "SUCCESS",
            actorId = appUser.id,
            sessionId = sessionId.toString(),
            requestId = requestId,
        )
        return IssuedSessionTokens(
            accessToken = accessToken,
            idToken = idToken,
            cookies = SessionCookies(refreshCookie, csrfTokenCookie),
        )
    }
}
