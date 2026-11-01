package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.UserSession
import com.docuhyphen.app.api.resource.model.UserSessionDto
import java.time.Instant

object UserSessionDtoMapper
{
    fun toDto(session: UserSession, now: Instant, currentSessionId: String?): UserSessionDto
    {
        val isActive = session.isActive &&
                session.revokedAt == null &&
                (session.expiresAt == null || session.expiresAt!!.toInstant().isAfter(now))
        return UserSessionDto(
            sessionId = session.sessionId.toString(),
            deviceId = session.deviceId,
            deviceName = session.deviceName,
            ipAddress = session.ipAddress,
            userAgent = session.userAgent,
            createdDate = session.createdDate.toInstant().toString(),
            lastSeenAt = session.lastSeenAt.toInstant().toString(),
            expiresAt = session.expiresAt?.toInstant()?.toString(),
            revokedAt = session.revokedAt?.toInstant()?.toString(),
            revocationReasonCode = session.revocationReasonCode,
            isActive = isActive,
            isCurrent = session.sessionId.toString() == currentSessionId,
        )
    }
}
