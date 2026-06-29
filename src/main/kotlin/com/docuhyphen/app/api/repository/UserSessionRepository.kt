package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.UserSession
import jakarta.enterprise.context.RequestScoped
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.util.UUID

@RequestScoped
class UserSessionRepository : BaseRepository<UserSession>(UserSession::class.java)
{
    fun findBySessionId(sessionId: UUID): UserSession?
    {
        return entityManager.createQuery(
            "SELECT s FROM UserSession s WHERE s.sessionId = :sessionId",
            UserSession::class.java,
        )
            .setParameter("sessionId", sessionId)
            .resultList
            .firstOrNull()
    }

    fun findActiveBySessionIdAndUserId(sessionId: UUID, userId: UUID, now: Timestamp): UserSession?
    {
        return entityManager.createQuery(
            "SELECT s FROM UserSession s WHERE s.sessionId = :sessionId AND s.appUser.id = :userId AND s.isActive = true AND s.revokedAt IS NULL AND (s.expiresAt IS NULL OR s.expiresAt > :now)",
            UserSession::class.java,
        )
            .setParameter("sessionId", sessionId)
            .setParameter("userId", userId)
            .setParameter("now", now)
            .resultList
            .firstOrNull()
    }

    @Transactional
    fun updateLastSeen(sessionId: UUID, lastSeenAt: Timestamp)
    {
        entityManager.createQuery(
            "UPDATE UserSession s SET s.lastSeenAt = :lastSeenAt WHERE s.sessionId = :sessionId",
        )
            .setParameter("lastSeenAt", lastSeenAt)
            .setParameter("sessionId", sessionId)
            .executeUpdate()
    }

    @Transactional
    fun updateLastAuthTime(sessionId: UUID, lastAuthTime: Timestamp)
    {
        entityManager.createQuery(
            "UPDATE UserSession s SET s.lastAuthTime = :lastAuthTime WHERE s.sessionId = :sessionId",
        )
            .setParameter("lastAuthTime", lastAuthTime)
            .setParameter("sessionId", sessionId)
            .executeUpdate()
    }

    @Transactional
    fun revokeSession(sessionId: UUID, revokedAt: Timestamp, reasonCode: String)
    {
        entityManager.createQuery(
            "UPDATE UserSession s SET s.isActive = false, s.revokedAt = :revokedAt, s.revocationReasonCode = :reasonCode WHERE s.sessionId = :sessionId",
        )
            .setParameter("revokedAt", revokedAt)
            .setParameter("reasonCode", reasonCode)
            .setParameter("sessionId", sessionId)
            .executeUpdate()
    }

    @Transactional
    fun revokeAllActiveForUser(userId: UUID, revokedAt: Timestamp, reasonCode: String)
    {
        entityManager.createQuery(
            "UPDATE UserSession s SET s.isActive = false, s.revokedAt = :revokedAt, s.revocationReasonCode = :reasonCode WHERE s.appUser.id = :userId AND s.isActive = true",
        )
            .setParameter("revokedAt", revokedAt)
            .setParameter("reasonCode", reasonCode)
            .setParameter("userId", userId)
            .executeUpdate()
    }

    fun findActiveSessionsForUser(userId: UUID, now: Timestamp): List<UserSession>
    {
        return entityManager.createQuery(
            "SELECT s FROM UserSession s WHERE s.appUser.id = :userId AND s.isActive = true AND s.revokedAt IS NULL AND (s.expiresAt IS NULL OR s.expiresAt > :now) ORDER BY s.lastSeenAt DESC",
            UserSession::class.java,
        )
            .setParameter("userId", userId)
            .setParameter("now", now)
            .resultList
    }

    fun findAllSessionsForUser(userId: UUID): List<UserSession>
    {
        return entityManager.createQuery(
            "SELECT s FROM UserSession s WHERE s.appUser.id = :userId ORDER BY s.lastSeenAt DESC, s.createdDate DESC",
            UserSession::class.java,
        )
            .setParameter("userId", userId)
            .resultList
    }

    /** Sessions that passed their absolute expiry and have not yet been explicitly revoked. */
    fun findExpiredActiveSessions(now: Timestamp): List<UserSession>
    {
        return entityManager.createQuery(
            "SELECT s FROM UserSession s WHERE s.isActive = true AND s.revokedAt IS NULL AND s.expiresAt IS NOT NULL AND s.expiresAt <= :now",
            UserSession::class.java,
        )
            .setParameter("now", now)
            .resultList
    }

    fun findAllActiveSessions(now: Timestamp): List<UserSession>
    {
        return entityManager.createQuery(
            "SELECT s FROM UserSession s WHERE s.isActive = true AND s.revokedAt IS NULL AND (s.expiresAt IS NULL OR s.expiresAt > :now)",
            UserSession::class.java,
        )
            .setParameter("now", now)
            .resultList
    }

    @Transactional
    fun deleteInactiveSessionForUser(sessionId: UUID, userId: UUID): Int
    {
        return entityManager.createQuery(
            "DELETE FROM UserSession s WHERE s.sessionId = :sessionId AND s.appUser.id = :userId AND s.isActive = false",
        )
            .setParameter("sessionId", sessionId)
            .setParameter("userId", userId)
            .executeUpdate()
    }
}


