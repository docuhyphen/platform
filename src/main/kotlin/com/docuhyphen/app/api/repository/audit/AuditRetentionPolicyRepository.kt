package com.docuhyphen.app.api.repository.audit

import com.docuhyphen.app.api.model.entity.AuditRetentionPolicy
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.NoResultException
import java.util.*

@RequestScoped
class AuditRetentionPolicyRepository : BaseRepository<AuditRetentionPolicy>(AuditRetentionPolicy::class.java)
{
    fun findByOrganizationAndCategory(organizationId: UUID, category: String): AuditRetentionPolicy? =
        findByOwner("ORGANIZATION", organizationId, category)

    fun findByOwner(ownerType: String, ownerId: UUID, category: String): AuditRetentionPolicy?
    {
        return try
        {
            entityManager.createQuery(
                "SELECT p FROM AuditRetentionPolicy p WHERE p.ownerType = :ownerType AND p.ownerId = :ownerId AND p.category = :category",
                AuditRetentionPolicy::class.java,
            )
                .setParameter("ownerType", ownerType)
                .setParameter("ownerId", ownerId)
                .setParameter("category", category)
                .singleResult
        }
        catch (e: NoResultException)
        {
            null
        }
    }

    fun findAllByOrganization(organizationId: UUID): List<AuditRetentionPolicy> =
        findAllByOwner("ORGANIZATION", organizationId)

    fun findAllByOwner(ownerType: String, ownerId: UUID): List<AuditRetentionPolicy>
    {
        return entityManager.createQuery(
            "SELECT p FROM AuditRetentionPolicy p WHERE p.ownerType = :ownerType AND p.ownerId = :ownerId",
            AuditRetentionPolicy::class.java,
        )
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .resultList
    }
}
