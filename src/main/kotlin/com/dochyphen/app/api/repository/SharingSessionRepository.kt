package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.entity.SharingSession
import com.dochyphen.app.api.model.entity.SharingSessionStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class SharingSessionRepository : BaseRepository<SharingSession>(SharingSession::class.java)
{
    fun userHasSharingSessions(userId: UUID): Boolean
    {
        val query = entityManager.createQuery(
            """
            SELECT COUNT(s) FROM SharingSession s 
            WHERE (s.initiator.id = :userId OR s.recipient.id = :userId) 
            AND s.isDeleted = false
        """.trimIndent(),
            Long::class.javaObjectType
        )
        query.setParameter("userId", userId)
        val count = query.singleResult
        return count > 0
    }

    fun findByInitiatorId(initiatorId: UUID): List<SharingSession>
    {
        val query = entityManager.createQuery(
            """
                SELECT s FROM SharingSession s 
                WHERE s.initiator.id = :initiatorId""".trimIndent(),
            SharingSession::class.java
        )

        query.setParameter("initiatorId", initiatorId)

        return query.resultList
    }

    fun findByRecipientId(recipient: UUID): List<SharingSession>
    {
        val query = entityManager.createQuery(
            "SELECT s FROM SharingSession s WHERE s.recipient.id = :recipientId",
            SharingSession::class.java
        )
        query.setParameter("recipientId", recipient)
        return query.resultList
    }

    @Transactional
    fun updateSessionName(sessionId: UUID, sessionName: String)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.sessionName = :sessionName WHERE s.id = :sessionId"
        )
        query.setParameter("sessionName", sessionName)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }

    @Transactional
    fun updateDescription(sessionId: UUID, description: String)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.description = :description WHERE s.id = :sessionId"
        )
        query.setParameter("description", description)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }

    @Transactional
    fun updateStatus(sessionId: UUID, status: SharingSessionStatus)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.status = :status WHERE s.id = :sessionId"
        )
        query.setParameter("status", status)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }


    @Transactional
    fun updateEndDate(sessionId: UUID, timestamp: Timestamp)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.endDate = :timestamp WHERE s.id = :sessionId"
        )
        query.setParameter("timestamp", timestamp)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }

    @Transactional
    fun updateRejectionReason(sessionId: UUID, rejectionReason: String?)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.rejectionReason = :rejectionReason WHERE s.id = :sessionId"
        )
        query.setParameter("rejectionReason", rejectionReason)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }

    @Transactional
    fun updateLastActivity(sessionId: UUID, lastActivity: Timestamp)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.lastActivity = :lastActivity WHERE s.id = :sessionId"
        )
        query.setParameter("lastActivity", lastActivity)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }

    @Transactional
    fun updateRequireRecipientSignIn(sessionId: UUID, requestRecipientSignIn: Boolean)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.requireRecipientSignIn = :requestRecipientSignIn WHERE s.id = :sessionId"
        )
        query.setParameter("requestRecipientSignIn", requestRecipientSignIn)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }

    @Transactional
    fun updateAllowDocumentAddition(sessionId: UUID, allowDocumentAddition: Boolean)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.allowDocumentAddition = :allowDocumentAddition WHERE s.id = :sessionId"
        )
        query.setParameter("allowDocumentAddition", allowDocumentAddition)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }

    @Transactional
    fun updateAllowDocumentDeletion(sessionId: UUID, allowDocumentDeletion: Boolean)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.allowDocumentDeletion = :allowDocumentDeletion WHERE s.id = :sessionId"
        )
        query.setParameter("allowDocumentDeletion", allowDocumentDeletion)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }

    @Transactional
    fun updateAllowDocumentDownload(sessionId: UUID, allowDocumentDownload: Boolean)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.allowDocumentDownload = :allowDocumentDownload WHERE s.id = :sessionId"
        )
        query.setParameter("allowDocumentDownload", allowDocumentDownload)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }

    @Transactional
    fun updateAllowDocumentUpdate(sessionId: UUID, allowDocumentUpdate: Boolean)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.allowDocumentUpdate = :allowDocumentUpdate WHERE s.id = :sessionId"
        )
        query.setParameter("allowDocumentUpdate", allowDocumentUpdate)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }

    @Transactional
    fun updateAllowDocumentUpload(sessionId: UUID, allowDocumentUpload: Boolean)
    {
        val query = entityManager.createQuery(
            "UPDATE SharingSession s SET s.allowDocumentUpload = :allowDocumentUpload WHERE s.id = :sessionId"
        )
        query.setParameter("allowDocumentUpload", allowDocumentUpload)
        query.setParameter("sessionId", sessionId)
        query.executeUpdate()
    }
}