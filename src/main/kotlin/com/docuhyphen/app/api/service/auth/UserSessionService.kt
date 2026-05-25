package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.UserSession
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.realtime.UserSessionInfo
import com.docuhyphen.app.api.repository.UserSessionRepository
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

    fun revokeSession(sessionId: UUID, reasonCode: RevocationReasonCode)
    {
        val appUserId = userSessionRepository.findBySessionId(sessionId)?.appUser?.id
        userSessionRepository.revokeSession(
            sessionId = sessionId,
            revokedAt = Timestamp.from(Instant.now()),
            reasonCode = reasonCode.name,
        )
        sessionRevocationCache.markRevoked(sessionId, reasonCode)
        // Push SESSION_REVOKED to the affected device and SESSION_REMOVED to all peers.
        realtimeEventService.notifySessionRevoked(sessionId, reasonCode.name)
        if (appUserId != null)
        {
            realtimeEventService.notifySessionRemoved(appUserId, sessionId, exceptUserSessionId = sessionId)
        }
    }

    fun revokeAllUserSessions(userId: UUID, reasonCode: RevocationReasonCode)
    {
        val activeSessions = userSessionRepository.findActiveSessionsForUser(userId, Timestamp.from(Instant.now()))
        userSessionRepository.revokeAllActiveForUser(
            userId = userId,
            revokedAt = Timestamp.from(Instant.now()),
            reasonCode = reasonCode.name,
        )
        sessionRevocationCache.markManyRevoked(activeSessions.map { it.sessionId }, reasonCode)
        // SignOutService.signOutByUserId / .signOut(outOfAllDevices=true) also notify via
        // RealtimeEventService.notifyAllSessionsRevoked. Doing it here as well covers any
        // direct caller of revokeAllUserSessions (e.g. SCIM deprovision).
        activeSessions.forEach { realtimeEventService.notifySessionRevoked(it.sessionId, reasonCode.name) }
    }

    fun listActiveSessions(userId: UUID): List<UserSession>
    {
        return userSessionRepository.findActiveSessionsForUser(userId, Timestamp.from(Instant.now()))
    }
}
