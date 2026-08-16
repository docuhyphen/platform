package com.docuhyphen.app.api.repository.auth

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.MfaRecord
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery
import java.sql.Timestamp

@RequestScoped
class MfaRecordRepository : BaseRepository<MfaRecord>(MfaRecord::class.java)
{
    fun findByEmail(email: String): MfaRecord?
    {
        val queryString = """
            SELECT m FROM MfaRecord m 
            INNER JOIN AppUser a ON m.appUser.id = a.id
            WHERE a.email = :appUserEmail
        """

        val query: TypedQuery<MfaRecord> = entityManager.createQuery(queryString, MfaRecord::class.java)
        query.setParameter("appUserEmail", email)
        query.maxResults = 1 // limit to just one result

        return query.resultList.firstOrNull()
    }

    fun findByEmailAndToken(token: String, email: String): MfaRecord?
    {
        val queryString = """
            SELECT m FROM MfaRecord m 
            INNER JOIN AppUser a ON m.appUser.id = a.id
            WHERE a.email = :appUserEmail 
            AND m.mfaToken = :mfaToken
        """

        val query: TypedQuery<MfaRecord> = entityManager.createQuery(queryString, MfaRecord::class.java)
        query.setParameter("appUserEmail", email)
        query.setParameter("mfaToken", token)
        query.maxResults = 1 // limit to just one result

        return query.resultList.firstOrNull()
    }

    fun findLatestByEmail(email: String): MfaRecord?
    {
        val queryString = """
            SELECT m FROM MfaRecord m 
            INNER JOIN AppUser a ON m.appUser.id = a.id
            WHERE a.email = :appUserEmail
            ORDER BY m.createdDate DESC
        """
        val query: TypedQuery<MfaRecord> = entityManager.createQuery(queryString, MfaRecord::class.java)
        query.setParameter("appUserEmail", email)
        query.maxResults = 1 // limit to just one result

        return query.resultList.firstOrNull()
    }

    fun findByTokenAndType(otp: String, mfaType: MultifactorAuthenticationType): MfaRecord?
    {
        val queryString = "SELECT m FROM MfaRecord m WHERE m.mfaType = :mfaType AND m.mfaToken = :mfaToken"
        val query: TypedQuery<MfaRecord> = entityManager.createQuery(
            queryString,
            MfaRecord::class.java
        )
        query.setParameter("mfaToken", otp)
        query.setParameter("mfaType", mfaType)
        return query.resultList.firstOrNull()
    }
    fun countRecentRequestsByEmailAndIp(email: String, ipAddress: String, timestamp: Timestamp): Long
    {
        val queryString = """
            SELECT COUNT(m) FROM MfaRecord m 
            INNER JOIN AppUser a ON m.appUser.id = a.id
            WHERE a.email = :appUserEmail 
            AND m.ipAddress = :ipAddress 
            AND m.createdDate >= :timestamp
        """

        val query = entityManager.createQuery(queryString, Long::class.java)
        query.setParameter("appUserEmail", email)
        query.setParameter("ipAddress", ipAddress)
        query.setParameter("timestamp", timestamp)

        return query.singleResult
    }

    fun findByEmailAndSessionId(email: String, sessionId: String): MfaRecord?
    {
        val queryString = """
            SELECT m FROM MfaRecord m 
            INNER JOIN AppUser a ON m.appUser.id = a.id
            WHERE LOWER(a.email) = LOWER(:appUserEmail)
            AND m.sessionId = :sessionId
        """

        val query: TypedQuery<MfaRecord> = entityManager.createQuery(queryString, MfaRecord::class.java)
        query.setParameter("appUserEmail", email)
        query.setParameter("sessionId", sessionId)
        query.maxResults = 1

        return query.resultList.firstOrNull()
    }

    fun invalidatePreviousSessions(email: String, currentSessionId: String): Int
    {
        val queryString = """
            UPDATE MfaRecord m 
            SET m.status = 'INVALIDATED' 
            WHERE m.appUser.id IN (SELECT a.id FROM AppUser a WHERE a.email = :appUserEmail)
            AND m.sessionId != :sessionId 
            AND m.status = 'PENDING'
        """

        val query = entityManager.createQuery(queryString)
        query.setParameter("appUserEmail", email)
        query.setParameter("sessionId", currentSessionId)

        return query.executeUpdate()
    }

    fun deletePendingSessionsByEmailExcept(email: String, currentSessionId: String): Int
    {
        val queryString = """
            DELETE FROM MfaRecord m
            WHERE m.appUser.id IN (
                SELECT a.id FROM AppUser a WHERE LOWER(a.email) = LOWER(:appUserEmail)
            )
            AND m.sessionId != :sessionId
            AND m.status = 'PENDING'
        """

        val query = entityManager.createQuery(queryString)
        query.setParameter("appUserEmail", email)
        query.setParameter("sessionId", currentSessionId)

        return query.executeUpdate()
    }

    fun updateStatusForExpiredRecords(expiryThreshold: Timestamp): Int
    {
        val queryString = """
            UPDATE MfaRecord m 
            SET m.status = 'EXPIRED' 
            WHERE m.expiryDateTime < :expiryThreshold 
            AND m.status = 'PENDING'
        """

        val query = entityManager.createQuery(queryString)
        query.setParameter("expiryThreshold", expiryThreshold)

        return query.executeUpdate()
    }
}
