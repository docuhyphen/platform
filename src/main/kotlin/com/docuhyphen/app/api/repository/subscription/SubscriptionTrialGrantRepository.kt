package com.docuhyphen.app.api.repository.subscription

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.SubscriptionTrialGrant
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialSource
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class SubscriptionTrialGrantRepository :
    BaseRepository<SubscriptionTrialGrant>(SubscriptionTrialGrant::class.java)
{
    fun hasAutomaticGrant(ownerType: SubscriptionOwnerType, ownerId: UUID): Boolean
    {
        return entityManager.createQuery(
            """SELECT COUNT(g) FROM SubscriptionTrialGrant g
               WHERE g.ownerType = :ownerType
                 AND g.ownerId = :ownerId
                 AND g.source = :source""",
            java.lang.Long::class.java,
        )
            .setParameter("ownerType", ownerType.name)
            .setParameter("ownerId", ownerId)
            .setParameter("source", SubscriptionTrialSource.AUTOMATIC.name)
            .singleResult > 0
    }

    fun findByOwner(ownerType: SubscriptionOwnerType, ownerId: UUID): List<SubscriptionTrialGrant>
    {
        return entityManager.createQuery(
            """SELECT g FROM SubscriptionTrialGrant g
               WHERE g.ownerType = :ownerType AND g.ownerId = :ownerId
               ORDER BY g.createdAt ASC""",
            SubscriptionTrialGrant::class.java,
        )
            .setParameter("ownerType", ownerType.name)
            .setParameter("ownerId", ownerId)
            .resultList
    }
}
