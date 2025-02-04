package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.interceptor.AuthTokenContext
import com.securedocsshare.app.api.model.SharingSession
import com.securedocsshare.app.api.repository.AppUserRepository
import com.securedocsshare.app.api.repository.DocumentCommentRepository
import com.securedocsshare.app.api.repository.SharingSessionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class SharingSessionRetrievalService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionRetrievalService::class.java)
    }

    fun getSharingSessionsForInitiator(initiatorId: UUID): List<SharingSession>
    {
        return sharingSessionRepository.findByInitiatorId(initiatorId)
    }

    fun getSharingSessionsForReceiver(receiverId: UUID): List<SharingSession>
    {
        return sharingSessionRepository.findByReceiverId(receiverId)
    }

    fun getSharingSession(sessionId: String)
    {
    }
}