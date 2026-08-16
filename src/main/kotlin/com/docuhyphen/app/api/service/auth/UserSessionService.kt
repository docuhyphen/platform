package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.UserSession
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.realtime.UserSessionInfo
import com.docuhyphen.app.api.repository.auth.UserSessionRepository
import com.docuhyphen.app.api.util.UserAgentParser
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@RequestScoped
class UserSessionService @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    private val sessionRevocationCache: SessionRevocationCache,
    private val realtimeEventService: RealtimeEventService,
    private val authSessionPolicyService: AuthSessionPolicyService,
)
{
    fun createSession(
        appUser: AppUser,
        maxSessionDurationHours: Long,
        userAgent: String? = null,
        ipAddress: String? = null,
    ): UserSession
    {
        val now = Timestamp.from(Instant.now())
        val expiresAt = Timestamp.from(Instant.now().plusSeconds(maxSessionDurationHours.coerceAtLeast(1) * 3600))
        val saved = userSessionRepository.save(
            UserSession().apply {
                this.appUser = appUser
                this.createdDate = now
                this.lastSeenAt = now
                this.expiresAt = expiresAt
                this.isActive = true
                this.userAgent = userAgent?.take(1024)
                this.ipAddress = ipAddress
                this.deviceName = UserAgentParser.parse(userAgent)
            }
        )
        // Best-effort: tell the user's existing sockets a new session just appeared.
        realtimeEventService.notifySessionCreated(
            appUserId = appUser.id,
            session = UserSessionInfo(
                sessionId = saved.sessionId.toString(),
                deviceName = saved.deviceName,
                ipAddress = saved.ipAddress,
                userAgent = saved.userAgent,
                createdDate = saved.createdDate.toInstant().toString(),
                lastSeenAt = saved.lastSeenAt.toInstant().toString(),
                expiresAt = saved.expiresAt?.toInstant()?.toString(),
            ),
            newUserSessionId = saved.sessionId,
        )
        return saved
    }

    fun isActiveSession(sessionId: UUID, userId: UUID): Boolean
    {
        return userSessionRepository.findActiveBySessionIdAndUserId(
            sessionId,
            userId,
            Timestamp.from(Instant.now()),
        ) != null
    }

    fun touchSession(sessionId: UUID)
    {
        userSessionRepository.updateLastSeen(sessionId, Timestamp.from(Instant.now()))
    }

    fun markFreshAuth(sessionId: UUID)
    {
        userSessionRepository.updateLastAuthTime(sessionId, Timestamp.from(Instant.now()))
    }

    fun findSession(sessionId: UUID): UserSession?
    {
        return userSessionRepository.findBySessionId(sessionId)
    }

    /**
     * @param notifyRevoked When true (default) the revoked device receives EXCHANGE_REVOKED and
     * its socket is closed; appropriate for server-initiated revocations (idle timeout, admin
     * kick, password change). Pass false for voluntary sign-out: the socket is closed silently so
     * the client's own navigation to /sign-in is not overridden by auth-session-expired.
     */
    fun revokeSession(sessionId: UUID, reasonCode: RevocationReasonCode, notifyRevoked: Boolean = true)
    {
        val appUserId = userSessionRepository.findBySessionId(sessionId)?.appUser?.id
        userSessionRepository.revokeSession(
            sessionId = sessionId,
            revokedAt = Timestamp.from(Instant.now()),
            reasonCode = reasonCode.name,
        )
        sessionRevocationCache.markRevoked(sessionId, reasonCode)
        if (notifyRevoked)
        {
            realtimeEventService.notifySessionRevoked(sessionId, reasonCode.name)
        }
        else
        {
            realtimeEventService.closeSessionSocket(sessionId)
        }
        if (appUserId != null)
        {
            realtimeEventService.notifySessionRemoved(appUserId, sessionId, exceptUserSessionId = sessionId)
        }
    }

    /** Revoke all sessions that have passed their absolute expiry and notify peer devices. */
    fun cleanupExpiredSessions(): Int
    {
        val now = Timestamp.from(Instant.now())
        val expired = userSessionRepository.findExpiredActiveSessions(now)
        if (expired.isEmpty()) return 0

        expired.forEach { session ->
            userSessionRepository.revokeSession(
                sessionId = session.sessionId,
                revokedAt = now,
                reasonCode = RevocationReasonCode.EXCHANGE_EXPIRED.name,
            )
            sessionRevocationCache.markRevoked(session.sessionId, RevocationReasonCode.EXCHANGE_EXPIRED)
            realtimeEventService.closeSessionSocket(session.sessionId)
            val appUserId = session.appUser?.id ?: return@forEach
            realtimeEventService.notifySessionRemoved(appUserId, session.sessionId, exceptUserSessionId = session.sessionId)
        }

        return expired.size
    }

    /** Revoke abandoned active sessions whose last activity exceeded the configured idle policy. */
    fun cleanupIdleTimedOutSessions(): Int
    {
        val nowInstant = Instant.now()
        val activeSessions = userSessionRepository.findAllActiveSessions(Timestamp.from(nowInstant))
        if (activeSessions.isEmpty()) return 0

        var revokedCount = 0
        activeSessions.forEach { session ->
            val appUser = session.appUser ?: return@forEach
            val lastSeen = session.lastSeenAt.toInstant()
            val idleLimitMinutes = runCatching { authSessionPolicyService.resolveForAppUser(appUser) }
                .getOrNull()
                ?.idleTimeoutMinutes
                ?: return@forEach
            val idleSeconds = java.time.Duration.between(lastSeen, nowInstant).seconds
            if (idleSeconds <= idleLimitMinutes * 60) return@forEach

            revokeSession(session.sessionId, RevocationReasonCode.SECURITY_POLICY)
            revokedCount++
        }

        return revokedCount
    }

    /**
     * @param sendNotifications When true (default, used by SCIM/deprovision callers) this method
     * also sends EXCHANGE_REVOKED to every affected socket. Pass false when the caller (SignOutService)
     * sends notifications itself BEFORE calling this method; that ordering ensures the WS messages
     * are dispatched while all sockets are still open, avoiding the race where a concurrent 401 from
     * the revocation cache causes Device B to tear down its socket before the notification arrives.
     */
    fun revokeAllUserSessions(userId: UUID, reasonCode: RevocationReasonCode, sendNotifications: Boolean = true)
    {
        val activeSessions = userSessionRepository.findActiveSessionsForUser(userId, Timestamp.from(Instant.now()))
        userSessionRepository.revokeAllActiveForUser(
            userId = userId,
            revokedAt = Timestamp.from(Instant.now()),
            reasonCode = reasonCode.name,
        )
        sessionRevocationCache.markManyRevoked(activeSessions.map { it.sessionId }, reasonCode)
        if (sendNotifications)
        {
            activeSessions.forEach { realtimeEventService.notifySessionRevoked(it.sessionId, reasonCode.name) }
        }
    }

    fun listActiveSessions(userId: UUID): List<UserSession>
    {
        return userSessionRepository.findActiveSessionsForUser(userId, Timestamp.from(Instant.now()))
    }

    fun listSessions(userId: UUID): List<UserSession>
    {
        cleanupExpiredSessions()
        cleanupIdleTimedOutSessions()
        return userSessionRepository.findAllSessionsForUser(userId)
    }

    fun isSessionOwnedByUser(sessionId: UUID, userId: UUID): Boolean
    {
        return userSessionRepository.findBySessionId(sessionId)?.appUser?.id == userId
    }

    fun deleteInactiveSession(sessionId: UUID, userId: UUID): Boolean
    {
        return userSessionRepository.deleteInactiveSessionForUser(sessionId, userId) > 0
    }
}
