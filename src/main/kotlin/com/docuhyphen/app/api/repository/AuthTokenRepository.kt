package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AuthToken
import jakarta.enterprise.context.RequestScoped
import java.util.UUID

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

    fun deleteAllByUserId(userId: UUID)
    {
        entityManager.createQuery(
            "DELETE FROM AuthToken s WHERE s.appUser.id = :userId"
        ).setParameter("userId", userId)
            .executeUpdate()
    }
}