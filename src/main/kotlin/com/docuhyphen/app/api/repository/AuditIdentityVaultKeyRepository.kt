package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AuditIdentityVaultKey
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.NoResultException

@RequestScoped
class AuditIdentityVaultKeyRepository : BaseRepository<AuditIdentityVaultKey>(AuditIdentityVaultKey::class.java)
{
    fun findBySubject(subjectType: String, subjectId: String): AuditIdentityVaultKey?
    {
        return try
        {
            entityManager.createQuery(
                "SELECT k FROM AuditIdentityVaultKey k WHERE k.subjectType = :subjectType AND k.subjectId = :subjectId",
                AuditIdentityVaultKey::class.java,
            )
                .setParameter("subjectType", subjectType)
                .setParameter("subjectId", subjectId)
                .singleResult
        }
        catch (e: NoResultException)
        {
            null
        }
    }
}
