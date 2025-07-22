package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.entity.Document
import com.dochyphen.app.api.model.entity.SharingSession
import com.dochyphen.app.api.model.entity.SharingSessionStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class SharingSessionRepository : BaseRepository<SharingSession>(SharingSession::class.java)
{
    fun userHasSharingSessions(userId: UUID): Boolean {
        val query = """
        SELECT COUNT(DISTINCT s) FROM SharingSession s
        LEFT JOIN s.recipient r
        LEFT JOIN s.recipientGroup rg
        LEFT JOIN s.participants p
        WHERE (s.initiator.id = :appUserId
               OR r.id = :appUserId
               OR EXISTS (SELECT m FROM OrganizationGroupMember m WHERE m.organizationGroup.id = rg.id AND m.appUser.id = :appUserId)
               OR EXISTS (SELECT sp FROM s.participants sp WHERE sp.appUser.id = :appUserId)
               OR EXISTS (SELECT sp FROM s.participants sp JOIN sp.organizationGroup og JOIN og.members m WHERE m.appUser.id = :appUserId))
        AND s.isDeleted = false
    """

        val emQuery = entityManager.createQuery(query, Long::class.java).also {
            it.setParameter("appUserId", userId)  // Fixed parameter name to match the query
        }

        val count = emQuery.singleResult ?: 0
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

    fun findByParticipatingAppUser(appUserId: UUID): List<SharingSession>
    {
        val query = """
            SELECT DISTINCT s FROM SharingSession s
        LEFT JOIN s.recipient r
        LEFT JOIN s.recipientGroup rg
        LEFT JOIN s.participants p
        WHERE (s.initiator.id = :appUserId
               OR r.id = :appUserId
               OR EXISTS (SELECT m FROM OrganizationGroupMember m WHERE m.organizationGroup.id = rg.id AND m.appUser.id = :appUserId)
               OR EXISTS (SELECT sp FROM s.participants sp WHERE sp.appUser.id = :appUserId)
               OR EXISTS (SELECT sp FROM s.participants sp JOIN sp.organizationGroup og JOIN og.members m WHERE m.appUser.id = :appUserId))
        AND s.isDeleted = false
        """
        return entityManager.createQuery(query, SharingSession::class.java)
            .setParameter("appUserId", appUserId)
            .resultList
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

    fun searchSessions(
        appUserId: UUID,
        query: String?,
        status: SharingSessionStatus?,
        initiatedBy: Boolean?,
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String
    ): List<SharingSession>
    {
        val queryBuilder = StringBuilder(
            """
        SELECT DISTINCT s FROM SharingSession s
        LEFT JOIN s.recipient r
        LEFT JOIN s.recipientGroup rg
        LEFT JOIN s.participants p
        WHERE (s.initiator.id = :appUserId
               OR r.id = :appUserId
               OR EXISTS (SELECT m FROM OrganizationGroupMember m WHERE m.organizationGroup.id = rg.id AND m.appUser.id = :appUserId)
               OR EXISTS (SELECT sp FROM s.participants sp WHERE sp.appUser.id = :appUserId)
               OR EXISTS (SELECT sp FROM s.participants sp JOIN sp.organizationGroup og JOIN og.members m WHERE m.appUser.id = :appUserId))
        AND s.isDeleted = false
    """
        )

        if (!query.isNullOrBlank())
        {
            queryBuilder.append(
                """
        AND (
            LOWER(s.sessionName) LIKE LOWER(:query)
            OR LOWER(s.description) LIKE LOWER(:query)
            OR LOWER(s.initiator.email) LIKE LOWER(:query)
            OR (r IS NOT NULL AND LOWER(r.email) LIKE LOWER(:query))
            OR (rg IS NOT NULL AND LOWER(rg.name) LIKE LOWER(:query))
        )
        """
            )
        }

        if (status != null)
        {
            queryBuilder.append(" AND s.status = :status")
        }

        if (initiatedBy != null)
        {
            if (initiatedBy)
            {
                queryBuilder.append(" AND s.initiator.id = :appUserId")
            }
            else
            {
                queryBuilder.append(
                    " AND (r.id = :appUserId " +
                            "OR EXISTS (SELECT m FROM OrganizationGroupMember m WHERE m.organizationGroup.id = rg.id AND m.appUser.id = :appUserId) " +
                            "OR EXISTS (SELECT sp FROM s.participants sp WHERE sp.appUser.id = :appUserId) " +
                            "OR EXISTS (SELECT sp FROM s.participants sp JOIN sp.organizationGroup og JOIN og.members m WHERE m.appUser.id = :appUserId))"
                )
            }
        }

        val validSortFields = setOf("createdDate", "sessionName", "lastActivity")
        val safeSort = if (validSortFields.contains(sortBy)) sortBy else "createdDate"
        val safeDirection = if (sortDirection.equals("ASC", ignoreCase = true)) "ASC" else "DESC"

        queryBuilder.append(" ORDER BY s.$safeSort $safeDirection")

        val jpaQuery = entityManager.createQuery(queryBuilder.toString(), SharingSession::class.java)
        jpaQuery.setParameter("appUserId", appUserId)

        if (!query.isNullOrBlank())
        {
            jpaQuery.setParameter("query", "%${query.trim()}%")
        }

        if (status != null)
        {
            jpaQuery.setParameter("status", status)
        }

        jpaQuery.firstResult = page * size
        jpaQuery.maxResults = size

        val sessions = jpaQuery.resultList

        sessions.forEach { session ->
            session.documents = session.documents.filter { !it.isDeleted } as MutableList<Document>
        }

        return sessions
    }

    fun countSearchResults(
        appUserId: UUID,
        query: String?,
        status: SharingSessionStatus?,
        initiatedBy: Boolean?
    ): Long
    {
        val queryBuilder = StringBuilder(
            """
        SELECT COUNT(DISTINCT s) FROM SharingSession s
        LEFT JOIN s.recipient r
        LEFT JOIN s.recipientGroup rg
        LEFT JOIN s.participants p
        WHERE (s.initiator.id = :appUserId
               OR r.id = :appUserId
               OR EXISTS (SELECT m FROM OrganizationGroupMember m WHERE m.organizationGroup.id = rg.id AND m.appUser.id = :appUserId)
               OR EXISTS (SELECT sp FROM s.participants sp WHERE sp.appUser.id = :appUserId)
               OR EXISTS (SELECT sp FROM s.participants sp JOIN sp.organizationGroup og JOIN og.members m WHERE m.appUser.id = :appUserId))
        AND s.isDeleted = false
    """
        )

        if (!query.isNullOrBlank())
        {
            queryBuilder.append(
                """
            AND (
                LOWER(s.sessionName) LIKE LOWER(:query)
                OR LOWER(s.description) LIKE LOWER(:query)
                OR LOWER(s.initiator.email) LIKE LOWER(:query)
                OR (r IS NOT NULL AND LOWER(r.email) LIKE LOWER(:query))
                OR (rg IS NOT NULL AND LOWER(rg.name) LIKE LOWER(:query))
            )
        """
            )
        }

        if (status != null)
        {
            queryBuilder.append(" AND s.status = :status")
        }

        if (initiatedBy != null)
        {
            if (initiatedBy)
            {
                queryBuilder.append(" AND s.initiator.id = :appUserId")
            }
            else
            {
                queryBuilder.append(
                    " AND (r.id = :appUserId " +
                            "OR EXISTS (SELECT m FROM OrganizationGroupMember m WHERE m.organizationGroup.id = rg.id AND m.appUser.id = :appUserId) " +
                            "OR EXISTS (SELECT sp FROM s.participants sp WHERE sp.appUser.id = :appUserId) " +
                            "OR EXISTS (SELECT sp FROM s.participants sp JOIN sp.organizationGroup og JOIN og.members m WHERE m.appUser.id = :appUserId))"
                )
            }
        }

        val jpaQuery = entityManager.createQuery(queryBuilder.toString(), Long::class.java)
        jpaQuery.setParameter("appUserId", appUserId)

        if (!query.isNullOrBlank())
        {
            jpaQuery.setParameter("query", "%${query.trim()}%")
        }

        if (status != null)
        {
            jpaQuery.setParameter("status", status)
        }

        return jpaQuery.singleResult
    }

    fun getAppUserLinkedSharingSessions(appUserId: UUID): List<SharingSession>
    {
        val query = entityManager.createQuery(
            """
            SELECT s FROM SharingSession s 
            WHERE (s.initiator.id = :appUserId OR s.recipient.id = :appUserId) 
            AND s.isDeleted = false
        """.trimIndent(),
            SharingSession::class.java
        )
        query.setParameter("appUserId", appUserId)
        return query.resultList ?: emptyList()
    }
}