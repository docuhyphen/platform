package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AuditRetentionPolicy
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.NoResultException
import java.util.UUID

@RequestScoped
class AuditRetentionPolicyRepository : BaseRepository<AuditRetentionPolicy>(AuditRetentionPolicy::class.java)
{
    fun findByOrganizationAndCategory(organizationId: UUID, category: String): AuditRetentionPolicy?
    {
        return try
        {
            entityManager.createQuery(
                "SELECT p FROM AuditRetentionPolicy p WHERE p.organizationId = :organizationId AND p.category = :category",
                AuditRetentionPolicy::class.java,
            )
                .setParameter("organizationId", organizationId)
                .setParameter("category", category)
                .singleResult
        }
        catch (e: NoResultException)
        {
            null
        }
    }

    fun findAllByOrganization(organizationId: UUID): List<AuditRetentionPolicy>
    {
        return entityManager.createQuery(
            "SELECT p FROM AuditRetentionPolicy p WHERE p.organizationId = :organizationId",
            AuditRetentionPolicy::class.java,
        )
            .setParameter("organizationId", organizationId)
            .resultList
    }
}
