package com.securedocsshare.app.api.repository

import com.securedocsshare.app.api.model.MfaRecord
import com.securedocsshare.app.api.model.MultifactorAuthenticationType
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery

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
}
