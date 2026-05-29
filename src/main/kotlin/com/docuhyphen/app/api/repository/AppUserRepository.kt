package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AppUser
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery

@RequestScoped
class AppUserRepository : BaseRepository<AppUser>(AppUser::class.java)
{
    fun findByEmail(email: String): AppUser?
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            "SELECT a FROM AppUser a WHERE LOWER(a.email) = LOWER(:email)",
            AppUser::class.java
        )
        query.setParameter("email", email)
        return query.resultList.firstOrNull()
    }

    /**
     * Variant that excludes temporary placeholder rows (created by the no-auth recipient
     * flow when an initiator shares with an email that doesn't yet have an account). Used
     * by the sign-up path so a temp row doesn't false-positive an "email already in use" check.
     */
    fun findActiveByEmail(email: String): AppUser?
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            "SELECT a FROM AppUser a WHERE LOWER(a.email) = LOWER(:email) AND a.isTemporary = false",
            AppUser::class.java
        )
        query.setParameter("email", email)
        return query.resultList.firstOrNull()
    }

    /** Returns a temp placeholder row if one exists for this email, otherwise null. */
    fun findTemporaryByEmail(email: String): AppUser?
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            "SELECT a FROM AppUser a WHERE LOWER(a.email) = LOWER(:email) AND a.isTemporary = true",
            AppUser::class.java
        )
        query.setParameter("email", email)
        return query.resultList.firstOrNull()
    }

    fun findAllActiveUsers(): List<AppUser>
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            "SELECT a FROM AppUser a WHERE a.isActive = true",
            AppUser::class.java
        )
        return query.resultList
    }

    fun detach(user: AppUser)
    {
        entityManager.detach(user)
    }
}
