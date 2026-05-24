package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.UserSession
import com.docuhyphen.app.api.repository.UserSessionRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@RequestScoped
class UserSessionService @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    private val sessionRevocationCache: SessionRevocationCache,
)
{
    fun createSession(appUser: AppUser, maxSessionDurationHours: Long): UserSession
    {
        val now = Timestamp.from(Instant.now())
        val expiresAt = Timestamp.from(Instant.now().plusSeconds(maxSessionDurationHours.coerceAtLeast(1) * 3600))
        return userSessionRepository.save(
            UserSession().apply {
                this.appUser = appUser
                this.createdDate = now
                this.lastSeenAt = now
                this.expiresAt = expiresAt
                this.isActive = true
            }
        )
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
        userSessionRepository.revokeSession(
            sessionId = sessionId,
            revokedAt = Timestamp.from(Instant.now()),
            reasonCode = reasonCode.name,
        )
        sessionRevocationCache.markRevoked(sessionId, reasonCode)
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
    }

    fun listActiveSessions(userId: UUID): List<UserSession>
    {
        return userSessionRepository.findActiveSessionsForUser(userId, Timestamp.from(Instant.now()))
    }
}



