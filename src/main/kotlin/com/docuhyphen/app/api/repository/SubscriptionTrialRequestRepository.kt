package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.SubscriptionTrialRequest
import com.docuhyphen.app.api.model.entity.SubscriptionTrialRequestStatus
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class SubscriptionTrialRequestRepository : BaseRepository<SubscriptionTrialRequest>(SubscriptionTrialRequest::class.java)
{
    fun insertAndFlush(request: SubscriptionTrialRequest): SubscriptionTrialRequest
    {
        entityManager.persist(request)
        entityManager.flush()
        return request
    }

    fun findLatest(ownerType: String, ownerId: UUID): SubscriptionTrialRequest? =
        entityManager.createQuery(
            """SELECT r FROM SubscriptionTrialRequest r
               WHERE r.ownerType = :ownerType AND r.ownerId = :ownerId
               ORDER BY r.requestedAt DESC""",
            SubscriptionTrialRequest::class.java,
        )
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .setMaxResults(1)
            .resultList
            .firstOrNull()

    fun findPending(ownerType: String, ownerId: UUID): SubscriptionTrialRequest? =
        entityManager.createQuery(
            """SELECT r FROM SubscriptionTrialRequest r
               WHERE r.ownerType = :ownerType AND r.ownerId = :ownerId AND r.status = :status""",
            SubscriptionTrialRequest::class.java,
        )
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .setParameter("status", SubscriptionTrialRequestStatus.PENDING.name)
            .resultList
            .firstOrNull()

    fun findByIdForDecision(id: UUID): SubscriptionTrialRequest? = findByIdForUpdate(id)

    fun list(status: SubscriptionTrialRequestStatus?, limit: Int, offset: Int): List<SubscriptionTrialRequest>
    {
        val filter = if (status == null) "" else " WHERE r.status = :status"
        val query = entityManager.createQuery(
            "SELECT r FROM SubscriptionTrialRequest r$filter ORDER BY r.requestedAt DESC",
            SubscriptionTrialRequest::class.java,
        )
        status?.let { query.setParameter("status", it.name) }
        return query.setFirstResult(offset).setMaxResults(limit).resultList
    }

    fun count(status: SubscriptionTrialRequestStatus?): Long
    {
        val filter = if (status == null) "" else " WHERE r.status = :status"
        val query = entityManager.createQuery(
            "SELECT COUNT(r) FROM SubscriptionTrialRequest r$filter",
            Long::class.java,
        )
        status?.let { query.setParameter("status", it.name) }
        return query.singleResult
    }
}
