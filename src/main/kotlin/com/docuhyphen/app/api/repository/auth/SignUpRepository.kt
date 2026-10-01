package com.docuhyphen.app.api.repository.auth

import com.docuhyphen.app.api.model.entity.SignUpEntity
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType

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

    fun findByEmailForUpdate(email: String): SignUpEntity?
    {
        return entityManager.createQuery(
            "SELECT s FROM SignUpEntity s WHERE LOWER(s.email) = LOWER(:email)",
            SignUpEntity::class.java
        )
            .setParameter("email", email)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .firstOrNull()
    }

    fun findByEmailForUpdateNoWait(email: String): SignUpEntity?
    {
        return entityManager.createQuery(
            "SELECT s FROM SignUpEntity s WHERE LOWER(s.email) = LOWER(:email)",
            SignUpEntity::class.java
        )
            .setParameter("email", email)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setHint(LOCK_TIMEOUT_HINT, NO_WAIT)
            .resultList
            .firstOrNull()
    }

    private companion object
    {
        const val LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout"
        const val NO_WAIT = 0
    }
}
