package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.SignUpEntity
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SignUpRepository : BaseRepository<SignUpEntity>(SignUpEntity::class.java)
{
    fun findByEmail(email: String): SignUpEntity?
    {
        val query = entityManager.createQuery(
            "SELECT s FROM SignUpEntity s WHERE LOWER(s.email) = LOWER(:email)",
            SignUpEntity::class.java
        )
        query.setParameter("email", email)
        return query.resultList.firstOrNull()
    }
}
