package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.IdentityProviderLink
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import jakarta.enterprise.context.RequestScoped
import java.util.UUID

@RequestScoped
class IdentityProviderLinkRepository : BaseRepository<IdentityProviderLink>(IdentityProviderLink::class.java)
{
    fun findByProviderAndExternalSubjectId(provider: IdentityProviderType, subjectId: String): IdentityProviderLink?
    {
        return entityManager.createQuery(
            "SELECT l FROM IdentityProviderLink l WHERE l.provider = :provider AND l.externalSubjectId = :subjectId",
            IdentityProviderLink::class.java
        )
            .setParameter("provider", provider)
            .setParameter("subjectId", subjectId)
            .resultList
            .firstOrNull()
    }

    fun findAllByAppUserId(userId: UUID): List<IdentityProviderLink>
    {
        return entityManager.createQuery(
            "SELECT l FROM IdentityProviderLink l WHERE l.appUser.id = :userId",
            IdentityProviderLink::class.java
        )
            .setParameter("userId", userId)
            .resultList
    }

    fun deleteByProviderAndAppUserId(provider: IdentityProviderType, userId: UUID)
    {
        entityManager.createQuery(
            "DELETE FROM IdentityProviderLink l WHERE l.provider = :provider AND l.appUser.id = :userId"
        )
            .setParameter("provider", provider)
            .setParameter("userId", userId)
            .executeUpdate()
    }

    fun countByAppUserId(userId: UUID): Long
    {
        return entityManager.createQuery(
            "SELECT COUNT(l) FROM IdentityProviderLink l WHERE l.appUser.id = :userId",
            Long::class.javaObjectType
        )
            .setParameter("userId", userId)
            .singleResult
    }
}

