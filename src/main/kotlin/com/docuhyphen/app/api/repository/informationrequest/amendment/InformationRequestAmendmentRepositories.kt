package com.docuhyphen.app.api.repository.informationrequest.amendment

import com.docuhyphen.app.api.model.entity.InformationRequestAmendment
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChange
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeIntent
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestAmendmentRepository :
    BaseRepository<InformationRequestAmendment>(InformationRequestAmendment::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestAmendment> =
        entityManager.createQuery(
            """
            SELECT amendment
            FROM InformationRequestAmendment amendment
            WHERE amendment.informationRequestId = :requestId
            ORDER BY amendment.amendmentNumber
            """.trimIndent(),
            InformationRequestAmendment::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}

@ApplicationScoped
class InformationRequestAmendmentChangeRepository :
    BaseRepository<InformationRequestAmendmentChange>(InformationRequestAmendmentChange::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestAmendmentChange> =
        entityManager.createQuery(
            """
            SELECT change
            FROM InformationRequestAmendmentChange change
            WHERE change.informationRequestId = :requestId
            ORDER BY change.requirementKey
            """.trimIndent(),
            InformationRequestAmendmentChange::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}

@ApplicationScoped
class InformationRequestNoticeIntentRepository :
    BaseRepository<InformationRequestNoticeIntent>(InformationRequestNoticeIntent::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestNoticeIntent> =
        entityManager.createQuery(
            """
            SELECT intent
            FROM InformationRequestNoticeIntent intent
            WHERE intent.informationRequestId = :requestId
            ORDER BY intent.createdAt, intent.id
            """.trimIndent(),
            InformationRequestNoticeIntent::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findForRequests(requestIds: Collection<UUID>): List<InformationRequestNoticeIntent>
    {
        if (requestIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT intent
            FROM InformationRequestNoticeIntent intent
            WHERE intent.informationRequestId IN :requestIds
            ORDER BY intent.createdAt, intent.id
            """.trimIndent(),
            InformationRequestNoticeIntent::class.java,
        )
            .setParameter("requestIds", requestIds)
            .resultList
    }

    fun findUnclaimedIds(limit: Int): List<UUID> =
        entityManager.createQuery(
            """
            SELECT intent.id
            FROM InformationRequestNoticeIntent intent
            WHERE NOT EXISTS (
                SELECT 1 FROM InformationRequestNoticeClaim claim WHERE claim.noticeIntentId = intent.id
            )
            ORDER BY intent.createdAt, intent.id
            """.trimIndent(),
            UUID::class.java,
        )
            .setMaxResults(limit)
            .resultList

    fun findUnclaimedIdsForRequest(requestId: UUID): List<UUID> =
        entityManager.createQuery(
            """
            SELECT intent.id
            FROM InformationRequestNoticeIntent intent
            WHERE intent.informationRequestId = :requestId
              AND NOT EXISTS (
                SELECT 1 FROM InformationRequestNoticeClaim claim WHERE claim.noticeIntentId = intent.id
            )
            ORDER BY intent.createdAt, intent.id
            """.trimIndent(),
            UUID::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}
