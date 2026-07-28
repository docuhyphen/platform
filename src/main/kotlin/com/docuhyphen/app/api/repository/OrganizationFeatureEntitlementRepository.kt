package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.OrganizationFeatureEntitlement
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class OrganizationFeatureEntitlementRepository :
    BaseRepository<OrganizationFeatureEntitlement>(OrganizationFeatureEntitlement::class.java)
{
    fun findByOrganizationId(organizationId: UUID): List<OrganizationFeatureEntitlement> =
        entityManager.createQuery(
            """SELECT e FROM OrganizationFeatureEntitlement e
               WHERE e.organizationId = :organizationId
               ORDER BY e.featureCode ASC""",
            OrganizationFeatureEntitlement::class.java,
        )
            .setParameter("organizationId", organizationId)
            .resultList

    fun findByOrganizationIds(
        organizationIds: Collection<UUID>,
    ): List<OrganizationFeatureEntitlement>
    {
        if (organizationIds.isEmpty())
        {
            return emptyList()
        }

        return entityManager.createQuery(
            """SELECT e FROM OrganizationFeatureEntitlement e
               WHERE e.organizationId IN :organizationIds
               ORDER BY e.organizationId ASC, e.featureCode ASC""",
            OrganizationFeatureEntitlement::class.java,
        )
            .setParameter("organizationIds", organizationIds)
            .resultList
    }
}
