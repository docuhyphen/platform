package com.docuhyphen.app.api.repository.subscription

import com.docuhyphen.app.api.model.entity.SubscriptionFeatureEntitlement
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class SubscriptionFeatureEntitlementRepository :
    BaseRepository<SubscriptionFeatureEntitlement>(SubscriptionFeatureEntitlement::class.java)
{
    fun findByOrganizationId(organizationId: UUID): List<SubscriptionFeatureEntitlement> =
        entityManager.createQuery(
            """SELECT e FROM SubscriptionFeatureEntitlement e
               WHERE e.organizationId = :organizationId
               ORDER BY e.featureCode ASC""",
            SubscriptionFeatureEntitlement::class.java,
        )
            .setParameter("organizationId", organizationId)
            .resultList

    fun findByOrganizationIds(
        organizationIds: Collection<UUID>,
    ): List<SubscriptionFeatureEntitlement>
    {
        if (organizationIds.isEmpty())
        {
            return emptyList()
        }

        return entityManager.createQuery(
            """SELECT e FROM SubscriptionFeatureEntitlement e
               WHERE e.organizationId IN :organizationIds
               ORDER BY e.organizationId ASC, e.featureCode ASC""",
            SubscriptionFeatureEntitlement::class.java,
        )
            .setParameter("organizationIds", organizationIds)
            .resultList
    }

    fun findByAppUserId(appUserId: UUID): List<SubscriptionFeatureEntitlement> =
        entityManager.createQuery(
            """SELECT e FROM SubscriptionFeatureEntitlement e
               WHERE e.appUserId = :appUserId
               ORDER BY e.featureCode ASC""",
            SubscriptionFeatureEntitlement::class.java,
        )
            .setParameter("appUserId", appUserId)
            .resultList
}
