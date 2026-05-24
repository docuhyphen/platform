package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import jakarta.enterprise.context.RequestScoped
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
}

