package com.docuhyphen.app.api.repository.subscription

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.UUID

@ApplicationScoped
class UserSubscriptionPolicyRepository :
    BaseRepository<UserSubscriptionPolicy>(UserSubscriptionPolicy::class.java)
{
    fun findByAppUserId(appUserId: UUID): UserSubscriptionPolicy?
    {
        return entityManager.createQuery(
            """SELECT p FROM UserSubscriptionPolicy p
               WHERE p.appUserId = :appUserId""",
            UserSubscriptionPolicy::class.java,
        )
            .setParameter("appUserId", appUserId)
            .resultList
            .firstOrNull()
    }

    /**
     * Loads the policy with a row-level write lock so quota checks and the work they authorize
     * commit as one serialized unit. Two concurrent requests cannot both consume the last
     * remaining allowance because the second waits for the first to commit.
     */
    fun findByAppUserIdForUpdate(appUserId: UUID): UserSubscriptionPolicy?
    {
        return entityManager.createQuery(
            """SELECT p FROM UserSubscriptionPolicy p
               WHERE p.appUserId = :appUserId""",
            UserSubscriptionPolicy::class.java,
        )
            .setParameter("appUserId", appUserId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .firstOrNull()
    }

    fun findByAppUserIds(appUserIds: Collection<UUID>): List<UserSubscriptionPolicy>
    {
        if (appUserIds.isEmpty())
        {
            return emptyList()
        }

        return entityManager.createQuery(
            """SELECT p FROM UserSubscriptionPolicy p
               WHERE p.appUserId IN :appUserIds
               ORDER BY p.appUserId ASC""",
            UserSubscriptionPolicy::class.java,
        )
            .setParameter("appUserIds", appUserIds)
            .resultList
    }

    fun countByPlanCode(planCode: String): Long
    {
        return entityManager.createQuery(
            """SELECT COUNT(p) FROM UserSubscriptionPolicy p
               WHERE p.planCode = :planCode""",
            java.lang.Long::class.java,
        )
            .setParameter("planCode", planCode)
            .singleResult
            .toLong()
    }
}

