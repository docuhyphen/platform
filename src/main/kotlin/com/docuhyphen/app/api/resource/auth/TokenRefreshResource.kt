package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.model.entity.AuthTokenType.REFRESH
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.TokenRefreshResponse
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.CsrfProtectionService
import com.docuhyphen.app.api.service.auth.RefreshRotationStatus
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.RiskLevel
import com.docuhyphen.app.api.service.auth.RiskSignalService
import com.docuhyphen.app.api.service.auth.AuthSessionPolicyService
import com.docuhyphen.app.api.service.auth.OrganizationMembershipValidationService
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.auth.SecurityIncidentService
import com.docuhyphen.app.api.service.auth.TokenIssuanceService
import com.docuhyphen.app.api.service.auth.UserSessionService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
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
    private val authSessionPolicyService: AuthSessionPolicyService,
    private val organizationMembershipValidationService: OrganizationMembershipValidationService,
    private val csrfProtectionService: CsrfProtectionService,
    private val authAuditService: AuthAuditService,
    private val authRateLimitService: AuthRateLimitService,
    private val configurationService: ConfigurationService,
    private val userSessionService: UserSessionService,
    private val securityIncidentService: SecurityIncidentService,
    private val riskSignalService: RiskSignalService,
    private val clientIpResolver: com.docuhyphen.app.api.service.auth.ClientIpResolver,
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
        @CookieParam("csrf_token") csrfCookie: Cookie?,
        @HeaderParam("X-CSRF-Token") csrfHeader: String?,
        @HeaderParam("Origin") originHeader: String?,
        @HeaderParam("Referer") refererHeader: String?,
        @HeaderParam("User-Agent") userAgent: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
        @Context request: io.vertx.core.http.HttpServerRequest,
    ): Response
    {
        return try
        {
            val clientIp = getClientIpAddress(request)
            if (authRateLimitService.isLimited(
                    key = "auth:refresh:$clientIp",
                    maxPerMinute = configurationService.getAuthRateLimitRefreshPerMinute(),
                ))
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
                return Response.status(429)
                    .entity(ResponseError("Too many requests. Please try again later."))
                    .build()
            }

            if (!csrfProtectionService.verify(csrfCookie?.value, csrfHeader, originHeader, refererHeader))
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.CSRF_VALIDATION_FAILURE,
                    severity = SecurityIncidentSeverity.HIGH,
                    requestId = requestId,
                    details = "origin=$originHeader;ip=$clientIp",
                )
                authAuditService.emit(
                    action = "TOKEN_REFRESH",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.CSRF_VALIDATION_FAILED,
                    requestId = requestId,
                )
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("CSRF validation failed"))
                    .build()
            }

            val refreshTokenValue = refreshTokenCookie?.value
            if (refreshTokenValue.isNullOrBlank())
            {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("No refresh token provided"))
                    .build()
            }

            // Opaque refresh token: cookie value is "{jti}.{secret}". Split, look up by jti
            // in Redis (server-side source of truth), then verify the secret against the stored hash.
            val parts = refreshTokenValue.split('.', limit = 2)
            if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank())
            {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid refresh token format"))
                    .build()
            }
            val jti = parts[0]
            val stored = authenticationService.findRefreshTokenByJti(jti)
                ?: return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid or expired refresh token"))
                    .build()

            // Constant-time hash comparison against the stored token hash.
            if (!authenticationService.verifyRefreshTokenSecret(refreshTokenValue, stored)) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid refresh token"))
                    .build()
            }

            val familyId = stored.familyId
            val sessionId = stored.sessionId
                ?: run {

                    authenticationService.deleteRefreshTokenByJti(jti)
                    logger.warn("Refresh token has no session association; forcing re-authentication")
                    return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseError("EXCHANGE_EXPIRED"))
                        .build()
                }

            val userId = stored.userId
            val appUser = appUserService.getById(userId)
                ?: return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("User not found"))
                    .build()

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
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("User is inactive"))
                    .build()
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
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError(membershipValidation.message ?: "Organization membership is inactive"))
                    .build()
            }

            // Per-session revocation check (replaces the older exchange_version JWT claim).
            // O(1) Redis lookup; entries TTL out after the refresh-token window.
            // The interceptor performs the same check on every authenticated request.
            if (!userSessionService.isActiveSession(sessionId, appUser.id))
            {
                authenticationService.deleteAllRefreshTokensForUser(appUser.id)
                authAuditService.emit(
                    action = "TOKEN_REFRESH",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.REFRESH_INVALID,
                    actorId = appUser.id,
                    sessionId = sessionId.toString(),
                    requestId = requestId,
                )
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Session is no longer active"))
                    .build()
            }

            // Risk-based check: compare current request context against the session's stored fingerprint.
            // HIGH risk (both IP /16 and UA family changed) → terminate the session and force re-auth.
            val session = userSessionService.findSession(sessionId)
            if (session != null)
            {
                val risk = riskSignalService.evaluate(session, clientIp, userAgent, requestId)
                if (risk.level == RiskLevel.HIGH)
                {
                    authenticationService.deleteAllRefreshTokensForUser(appUser.id, RevocationReasonCode.RISK_SIGNAL_DETECTED)
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
                    return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseError("Session terminated for security reasons."))
                        .build()
                }
            }

            val policy = authSessionPolicyService.resolveForAppUser(appUser)

            // Sliding inactivity (idle) timeout enforcement. If the session has been quiet
            // longer than the per-org idle policy permits, terminate it. Critical for the
            // lost-device scenario where the legitimate user simply stopped working.
            val sessionForIdle = userSessionService.findSession(sessionId)
            val lastSeenInstant = sessionForIdle?.lastSeenAt?.toInstant()
            if (lastSeenInstant != null)
            {
                val idleSeconds = java.time.Duration.between(lastSeenInstant, java.time.Instant.now()).seconds
                val idleLimitSeconds = policy.idleTimeoutMinutes * 60
                if (idleSeconds > idleLimitSeconds)
                {
                    authenticationService.deleteAllRefreshTokensForUser(appUser.id, RevocationReasonCode.SECURITY_POLICY)
                    userSessionService.revokeSession(sessionId, RevocationReasonCode.SECURITY_POLICY)
                    authAuditService.emit(
                        action = "TOKEN_REFRESH",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        actorId = appUser.id,
                        sessionId = sessionId.toString(),
                        requestId = requestId,
                        reason = "Idle timeout exceeded (idleSeconds=$idleSeconds, limit=$idleLimitSeconds)",
                    )
                    return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseError("Session timed out due to inactivity"))
                        .build()
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
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Refresh token has been revoked"))
                    .build()
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
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid refresh token"))
                    .build()
            }

            val successorToken = if (rotationEnabled)
            {
                rotation?.successorToken
                    ?: return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseError("Invalid refresh token state"))
                        .build()
            }
            else
            {
                val stored = authenticationService.findRefreshTokenByJti(jti)
                if (stored == null || stored.userId != appUser.id || stored.status.equals("REVOKED", ignoreCase = true))
                {
                    authAuditService.emit(
                        action = "TOKEN_REFRESH",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.REFRESH_INVALID,
                        actorId = appUser.id,
                        requestId = requestId,
                    )
                    return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseError("Invalid refresh token"))
                        .build()
                }
                refreshTokenValue
            }

            // Preserve auth_time from the session,  refresh is not a re-auth, so the freshness
            // window must not be reset just because the access token was renewed.
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
            userSessionService.touchSession(sessionId)

            Response.ok(TokenRefreshResponse(accessToken, idToken))
                .cookie(refreshCookie, csrfTokenCookie)
                .build()
                .also {
                    authAuditService.emit(
                        action = "TOKEN_REFRESH",
                        outcome = "SUCCESS",
                        actorId = appUser.id,
                        sessionId = sessionId.toString(),
                        requestId = requestId,
                    )
                }
        }
        catch (e: Exception)
        {
            logger.error("Error refreshing token", e)

            Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Failed to refresh token"))
                .build()
        }
    }

    private fun getClientIpAddress(request: io.vertx.core.http.HttpServerRequest): String =
        clientIpResolver.resolve(request)
}
