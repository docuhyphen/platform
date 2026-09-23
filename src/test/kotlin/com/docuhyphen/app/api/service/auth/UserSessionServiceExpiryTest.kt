package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.UserSession
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.auth.UserSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant

class UserSessionServiceExpiryTest
{
    private val repository = mock<UserSessionRepository>()
    private val revocationCache = mock<SessionRevocationCache>()
    private val realtimeEventService = mock<RealtimeEventService>()
    private val policyService = mock<AuthSessionPolicyService>()
    private val service = UserSessionService(repository, revocationCache, realtimeEventService, policyService)

    @Test
    fun `absolute expiry notifies the session with a session expired reason`()
    {
        val session = UserSession().apply {
            appUser = AppUser()
            expiresAt = Timestamp.from(Instant.now().minusSeconds(1))
        }
        whenever(repository.findExpiredActiveSessions(any())).thenReturn(listOf(session))
        whenever(repository.findBySessionId(session.sessionId)).thenReturn(session)

        assertEquals(1, service.cleanupExpiredSessions())

        verify(repository).revokeSession(eq(session.sessionId), any(), eq(RevocationReasonCode.SESSION_EXPIRED.name))
        verify(revocationCache).markRevoked(session.sessionId, RevocationReasonCode.SESSION_EXPIRED)
        verify(realtimeEventService).notifySessionRevoked(session.sessionId, RevocationReasonCode.SESSION_EXPIRED.name)
    }

    @Test
    fun `idle cleanup uses the inactivity timeout reason`()
    {
        val appUser = AppUser()
        val session = UserSession().apply {
            this.appUser = appUser
            lastSeenAt = Timestamp.from(Instant.now().minusSeconds(121))
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3_600))
        }
        whenever(repository.findAllActiveSessions(any())).thenReturn(listOf(session))
        whenever(repository.findBySessionId(session.sessionId)).thenReturn(session)
        whenever(policyService.resolveForAppUser(appUser)).thenReturn(AuthSessionPolicy(15, 60, 8, 2))

        assertEquals(1, service.cleanupIdleTimedOutSessions())

        verify(repository).revokeSession(eq(session.sessionId), any(), eq(RevocationReasonCode.INACTIVITY_TIMEOUT.name))
        verify(revocationCache).markRevoked(session.sessionId, RevocationReasonCode.INACTIVITY_TIMEOUT)
        verify(realtimeEventService).notifySessionRevoked(session.sessionId, RevocationReasonCode.INACTIVITY_TIMEOUT.name)
    }
}
