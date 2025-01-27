package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.model.SharingSession
import com.securedocsshare.app.api.repository.SharingSessionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class UserSessionService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository
) {
    fun getAllSessionsForUser(userId: UUID): List<SharingSession> {
        val initiatedSessions = sharingSessionRepository.findByInitiatorId(userId)
        val receivedSessions = sharingSessionRepository.findByReceiverId(userId)
        return initiatedSessions + receivedSessions
    }
}