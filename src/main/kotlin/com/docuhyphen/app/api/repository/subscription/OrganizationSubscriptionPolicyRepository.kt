package com.docuhyphen.app.api.repository.subscription

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.LockModeType
import java.util.UUID

@RequestScoped
class OrganizationSubscriptionPolicyRepository : BaseRepository<OrganizationSubscriptionPolicy>(OrganizationSubscriptionPolicy::class.java)
{
    fun findByOrganizationId(organizationId: UUID): OrganizationSubscriptionPolicy?
    {
        return entityManager.createQuery(
            "SELECT p FROM OrganizationSubscriptionPolicy p WHERE p.organization.id = :organizationId",
            OrganizationSubscriptionPolicy::class.java,
        )
            .setParameter("organizationId", organizationId)
            .resultList
            .firstOrNull()
    }

    fun findByOrganizationIdForUpdate(organizationId: UUID): OrganizationSubscriptionPolicy?
    {
        return entityManager.createQuery(
            "SELECT p FROM OrganizationSubscriptionPolicy p WHERE p.organization.id = :organizationId",
            OrganizationSubscriptionPolicy::class.java,
        )
            .setParameter("organizationId", organizationId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .firstOrNull()
    }

    fun findByOrganizationIds(organizationIds: Collection<UUID>): List<OrganizationSubscriptionPolicy>
    {
        if (organizationIds.isEmpty())
        {
            return emptyList()
        }

        return entityManager.createQuery(
            """SELECT p FROM OrganizationSubscriptionPolicy p
               WHERE p.organization.id IN :organizationIds""",
            OrganizationSubscriptionPolicy::class.java,
        )
            .setParameter("organizationIds", organizationIds)
            .resultList
    }
}

