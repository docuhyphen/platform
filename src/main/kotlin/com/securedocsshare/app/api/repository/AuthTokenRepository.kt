package com.securedocsshare.app.api.repository

import com.securedocsshare.app.api.model.AuthToken
import jakarta.enterprise.context.RequestScoped

@RequestScoped
class AuthTokenRepository : BaseRepository<AuthToken>(AuthToken::class.java)
{
    fun findByToken(authToken: String): AuthToken?
    {
        return entityManager.createQuery(
            "SELECT s FROM AuthToken s WHERE s.token = :token", AuthToken::class.java
        ).setParameter("token", authToken)
            .resultList
            .firstOrNull()
    }
}