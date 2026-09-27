package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.sql.Timestamp
import java.util.UUID

/**
 * Persistence for runtime Information Request aggregates. Owner lookups always include owner type
 * and owner ID so organization and personal owners never share a key space by accident.
 */
@ApplicationScoped
class InformationRequestRepository :
    BaseRepository<InformationRequest>(InformationRequest::class.java)
{
    fun flushPendingChanges() = entityManager.flush()

    fun findRequestByIdForUpdate(id: UUID): InformationRequest?
    {
        val request = findById(id) ?: return null
        val parent = entityManager.find(com.docuhyphen.app.api.model.entity.Exchange::class.java, request.exchangeId)
            ?: return null
        entityManager.refresh(parent, LockModeType.PESSIMISTIC_WRITE)
        entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE)
        return request
    }

    fun findForExchange(exchangeId: UUID): List<InformationRequest> =
        entityManager.createQuery(
            """
            SELECT request
            FROM InformationRequest request
            WHERE request.exchangeId = :exchangeId
            ORDER BY request.createdAt, request.id
            """.trimIndent(),
            InformationRequest::class.java,
        )
            .setParameter("exchangeId", exchangeId)
            .resultList

    fun findForOwner(ownerType: InformationRequestOwnerType, ownerId: UUID): List<InformationRequest>
    {
        val ownerClause = when (ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION -> "request.ownerOrganizationId = :ownerId"
            InformationRequestOwnerType.USER -> "request.ownerUserId = :ownerId"
        }
        return entityManager.createQuery(
            """
            SELECT request
            FROM InformationRequest request
            WHERE request.ownerType = :ownerType
              AND $ownerClause
            ORDER BY request.createdAt, request.id
            """.trimIndent(),
            InformationRequest::class.java,
        )
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .resultList
    }

    fun findFinishedIdsBefore(ownerType: InformationRequestOwnerType, ownerId: UUID, finishedBefore: Timestamp, limit: Int): List<UUID>
    {
        val ownerClause = when (ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION -> "request.ownerOrganizationId = :ownerId"
            InformationRequestOwnerType.USER -> "request.ownerUserId = :ownerId"
        }
        return entityManager.createQuery(
            """
            SELECT request.id
            FROM InformationRequest request
            WHERE request.ownerType = :ownerType
              AND $ownerClause
              AND request.state IN :finished
              AND COALESCE(request.closedAt, request.cancelledAt, request.supersededAt, request.expiredAt) < :finishedBefore
            ORDER BY request.createdAt, request.id
            """.trimIndent(),
            UUID::class.java,
        )
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .setParameter("finished", InformationRequestState.entries.filter { it.isTerminal })
            .setParameter("finishedBefore", finishedBefore)
            .setMaxResults(limit)
            .resultList
    }
}
