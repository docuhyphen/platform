package com.docuhyphen.app.api.repository.subscription

import com.docuhyphen.app.api.model.entity.SubscriptionFeatureEntitlement
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Reads the platform-administered feature decisions recorded for one subscription owner.
 *
 * Each lookup names the owner column that belongs to its owner kind rather than matching an id
 * against both, so an organization and a person that happen to share an id never see each other's
 * decisions.
 */
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
