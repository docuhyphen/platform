package com.docuhyphen.app.api.repository.informationrequest.amendment

import com.docuhyphen.app.api.model.entity.InformationRequestAmendment
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChange
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeIntent
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

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

    fun findUnclaimedIds(limit: Int, maximumRenderFailures: Int = Int.MAX_VALUE): List<UUID> =
        entityManager.createNativeQuery(
            """
            SELECT intent.id
            FROM information_request_notice_intent intent
                     LEFT JOIN (SELECT notice_intent_id, COUNT(*) AS failures
                                FROM information_request_notice_render_failure
                                GROUP BY notice_intent_id) failed ON failed.notice_intent_id = intent.id
            WHERE NOT EXISTS (SELECT 1 FROM information_request_notice_claim claim WHERE claim.notice_intent_id = intent.id)
              AND COALESCE(failed.failures, 0) < :maximum
            ORDER BY COALESCE(failed.failures, 0), intent.created_at, intent.id
            LIMIT :limit
            """.trimIndent(),
        )
            .setParameter("maximum", maximumRenderFailures)
            .setParameter("limit", limit)
            .resultList
            .map { it as UUID }

    fun findUnclaimedIdsForRequest(requestId: UUID, maximumRenderFailures: Int = Int.MAX_VALUE): List<UUID> =
        entityManager.createNativeQuery(
            """
            SELECT intent.id
            FROM information_request_notice_intent intent
                     LEFT JOIN (SELECT notice_intent_id, COUNT(*) AS failures
                                FROM information_request_notice_render_failure
                                GROUP BY notice_intent_id) failed ON failed.notice_intent_id = intent.id
            WHERE intent.information_request_id = :requestId
              AND NOT EXISTS (SELECT 1 FROM information_request_notice_claim claim WHERE claim.notice_intent_id = intent.id)
              AND COALESCE(failed.failures, 0) < :maximum
            ORDER BY COALESCE(failed.failures, 0), intent.created_at, intent.id
            """.trimIndent(),
        )
            .setParameter("requestId", requestId)
            .setParameter("maximum", maximumRenderFailures)
            .resultList
            .map { it as UUID }
}
