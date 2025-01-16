package com.securedocsshare.app.api.repository

import com.securedocsshare.app.api.model.AppUser
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery

@RequestScoped
class AppUserRepository : BaseRepository<AppUser>(AppUser::class.java)
{
    fun findByEmail(email: String): AppUser?
    {
        val query: TypedQuery<AppUser> = entityManager.createQuery(
            "SELECT a FROM AppUser a WHERE a.email = :email",
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
