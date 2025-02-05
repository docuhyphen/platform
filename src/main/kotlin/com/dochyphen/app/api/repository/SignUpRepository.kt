package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.SignUpEntity
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SignUpRepository : BaseRepository<SignUpEntity>(SignUpEntity::class.java)
{
    fun findByEmail(email: String): SignUpEntity?
    {
        val query = entityManager.createQuery(
            "SELECT s FROM SignUpEntity s WHERE s.email = :email",
            SignUpEntity::class.java
        )
        query.setParameter("email", email)
        return query.resultList.firstOrNull()
    }
}
