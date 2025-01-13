package com.securedocsshare.app.repository

import com.securedocsshare.app.api.model.AuthToken
import jakarta.enterprise.context.RequestScoped

@RequestScoped
class AuthTokenRepository : BaseRepository<AuthToken>(AuthToken::class.java)
{
    fun getByToken(authToken: String): AuthToken?
    {
        val query = entityManager.createQuery(
            "SELECT s FROM AuthToken s WHERE s.token = :token",
            AuthToken::class.java
        )
        query.setParameter("token", authToken)

        return query.resultList.firstOrNull()
    }
}
