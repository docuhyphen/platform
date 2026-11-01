package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.UserSessionDtoMapper
import com.docuhyphen.app.api.resource.model.UserSessionDto
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant
import java.util.*

class UserSessionNotFoundException : RuntimeException("Session not found.")

@ApplicationScoped
class UserSessionManagementService @Inject constructor(
    private val userSessionService: UserSessionService,
    private val authTokenContext: AuthTokenContext,
    private val authAuditService: AuthAuditService,
    private val authenticationService: AuthenticationService,
)
{
    fun listSessions(): List<UserSessionDto>
    {
        val appUser = authTokenContext.authToken.appUser!!
        val sessions = userSessionService.listSessions(appUser.id)
        val now = Instant.now()
        val currentSessionId = authenticationService
            .verifyAccessToken(authTokenContext.authToken.token)
            ?.let { it["exchange_id"] as? String }
        return sessions.map { UserSessionDtoMapper.toDto(it, now, currentSessionId) }
    }

    fun revokeSession(sessionId: String)
    {
        val appUser = authTokenContext.authToken.appUser!!
        val sid = parseSessionId(sessionId)

        if (!userSessionService.isSessionOwnedByUser(sid, appUser.id))
        {
            throw UserSessionNotFoundException()
        }

        userSessionService.revokeSession(sid, RevocationReasonCode.LOGOUT_DEVICE)
        authAuditService.emit(
            action = "EXCHANGE_REVOKE",
            outcome = "SUCCESS",
            sessionId = sid.toString(),
            actorId = appUser.id,
            reasonCode = RevocationReasonCode.LOGOUT_DEVICE,
        )
    }

    fun deleteSessionRecord(sessionId: String)
    {
        val appUser = authTokenContext.authToken.appUser!!
        val sid = parseSessionId(sessionId)

        if (!userSessionService.isSessionOwnedByUser(sid, appUser.id))
        {
            throw UserSessionNotFoundException()
        }

        val deleted = userSessionService.deleteInactiveSession(sid, appUser.id)
        if (!deleted)
        {
            throw IllegalStateException("Only ended sessions can be deleted.")
        }

        authAuditService.emit(
            action = "SESSION_DELETE_RECORD",
            outcome = "SUCCESS",
            sessionId = sid.toString(),
            actorId = appUser.id,
        )
    }

    private fun parseSessionId(sessionId: String): UUID =
        runCatching { UUID.fromString(sessionId) }.getOrElse { throw IllegalArgumentException("Invalid session ID.") }
}
