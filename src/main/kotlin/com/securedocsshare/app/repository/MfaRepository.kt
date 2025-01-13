package com.securedocsshare.app.repository

import com.securedocsshare.app.api.model.MfaRecord
import com.securedocsshare.app.api.model.MultifactorAuthenticationType
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery

@RequestScoped
class MfaRepository : BaseRepository<MfaRecord>(MfaRecord::class.java)
{
    fun findMfaRecordByOtpAndType(otp: String, mfaType: MultifactorAuthenticationType): MfaRecord?
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

    fun findMfaRecordByEmailAndToken(email: String, otp: String): MfaRecord?
    {
        val queryString = """
            SELECT m FROM MfaRecord m 
            INNER JOIN AppUser a ON m.appUser.id = a.id
            WHERE m.mfaToken = :mfaToken 
            AND a.email = :appUserEmail
        """

        val query: TypedQuery<MfaRecord> = entityManager.createQuery(queryString, MfaRecord::class.java)
        query.setParameter("mfaToken", otp)
        query.setParameter("appUserEmail", email)
        query.maxResults = 1 // limit to just one result

        return query.resultList.firstOrNull()
    }
}
