package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.Document
import com.dochyphen.app.api.model.entity.SharingSession
import com.dochyphen.app.api.repository.SharingSessionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class SharingSessionRetrievalService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val authTokenContext: AuthTokenContext
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionRetrievalService::class.java)
    }

    fun getSharingSession(sessionId: String): SharingSession
    {
        val session = sharingSessionRepository.findById(UUID.fromString(sessionId)) ?: throw SharingSessionNotFoundException("Sharing session not found")

        session.documents = session.documents.filter { it.isDeleted == false } as MutableList<Document>

        return session
    }

    fun getAllSessionsForSignedInAppUser(): List<SharingSession>
    {
        val appUserId = authTokenContext.authToken.appUser?.id
        val initiatedSessions = sharingSessionRepository.findByInitiatorId(appUserId!!)
        val receivedSessions = sharingSessionRepository.findByRecipientId(appUserId)

        return (initiatedSessions + receivedSessions)
            .sortedByDescending { it.createdDate }
            .filter { !it.isDeleted }
            .map { session ->
                session.apply {
                    documents = documents.filter { !it.isDeleted } as MutableList<Document>
                }
            }
    }
}