package com.docuhyphen.app.api.repository.informationrequest.notice

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestNoticeClaimRepository :
    BaseRepository<InformationRequestNoticeClaim>(InformationRequestNoticeClaim::class.java)
{
    fun claim(intentId: UUID, requestId: UUID, worker: String, at: Timestamp): Boolean =
        entityManager.createNativeQuery(
            """
            INSERT INTO information_request_notice_claim (notice_intent_id, information_request_id, claimed_by, claimed_at)
            VALUES (:intent, :request, :worker, :at)
            ON CONFLICT (notice_intent_id) DO NOTHING
            """.trimIndent(),
        )
            .setParameter("intent", intentId)
            .setParameter("request", requestId)
            .setParameter("worker", worker)
            .setParameter("at", at)
            .executeUpdate() == 1

    fun findForRequest(requestId: UUID): List<InformationRequestNoticeClaim> =
        entityManager.createQuery(
            "SELECT claim FROM InformationRequestNoticeClaim claim WHERE claim.informationRequestId = :requestId",
            InformationRequestNoticeClaim::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findForRequests(requestIds: Collection<UUID>): List<InformationRequestNoticeClaim>
    {
        if (requestIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "SELECT claim FROM InformationRequestNoticeClaim claim WHERE claim.informationRequestId IN :requestIds",
            InformationRequestNoticeClaim::class.java,
        )
            .setParameter("requestIds", requestIds)
            .resultList
    }

    fun findForIntent(intentId: UUID): InformationRequestNoticeClaim? =
        entityManager.find(InformationRequestNoticeClaim::class.java, intentId)
}

@ApplicationScoped
class InformationRequestOutboundNoticeRepository :
    BaseRepository<InformationRequestOutboundNotice>(InformationRequestOutboundNotice::class.java)
{
    fun findForIntent(intentId: UUID): InformationRequestOutboundNotice? =
        entityManager.createQuery(
            "SELECT notice FROM InformationRequestOutboundNotice notice WHERE notice.noticeIntentId = :intentId",
            InformationRequestOutboundNotice::class.java,
        )
            .setParameter("intentId", intentId)
            .resultList
            .firstOrNull()

    fun findForRequest(requestId: UUID): List<InformationRequestOutboundNotice> =
        entityManager.createQuery(
            """
            SELECT notice FROM InformationRequestOutboundNotice notice
            WHERE notice.informationRequestId = :requestId
            ORDER BY notice.renderedAt, notice.id
            """.trimIndent(),
            InformationRequestOutboundNotice::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findForRequests(requestIds: Collection<UUID>): List<InformationRequestOutboundNotice>
    {
        if (requestIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "SELECT notice FROM InformationRequestOutboundNotice notice WHERE notice.informationRequestId IN :requestIds",
            InformationRequestOutboundNotice::class.java,
        )
            .setParameter("requestIds", requestIds)
            .resultList
    }

    fun findAwaitingDeliveryIds(
        maximumAttempts: Int,
        limit: Int,
        requestId: UUID? = null,
        now: java.time.Instant = java.time.Instant.now(),
    ): List<UUID>
    {
        val requestClause = if (requestId != null) "AND notice.information_request_id = :requestId" else ""
        val query = entityManager.createNativeQuery(
            """
            SELECT notice.id FROM information_request_outbound_notice notice
            WHERE notice.endpoint_state = 'RESOLVED'
              $requestClause
              AND NOT EXISTS (
                SELECT 1 FROM information_request_notice_delivery_attempt settled
                WHERE settled.outbound_notice_id = notice.id AND settled.outcome <> 'FAILED'
              )
              AND (SELECT COUNT(*) FROM information_request_notice_delivery_attempt attempt
                   WHERE attempt.outbound_notice_id = notice.id) < :maximumAttempts
              AND (NOT EXISTS (
                SELECT 1 FROM information_request_notice_delivery_attempt attempt WHERE attempt.outbound_notice_id = notice.id
              ) OR (SELECT MAX(attempt.attempted_at) + interval '5 minutes' * power(2, GREATEST(COUNT(*) - 1, 0))
                    FROM information_request_notice_delivery_attempt attempt
                    WHERE attempt.outbound_notice_id = notice.id) <= :now)
            ORDER BY notice.rendered_at, notice.id
            """.trimIndent(),
            UUID::class.java,
        )
            .setParameter("maximumAttempts", maximumAttempts)
            .setParameter("now", Timestamp.from(now))
            .setMaxResults(limit)
        requestId?.let { query.setParameter("requestId", it) }
        return query.resultList.map { it as UUID }
    }
}

@ApplicationScoped
class InformationRequestNoticeSequenceAllocationRepository :
    BaseRepository<InformationRequestNoticeSequenceAllocation>(InformationRequestNoticeSequenceAllocation::class.java)
{
    fun findForNotice(noticeId: UUID): List<InformationRequestNoticeSequenceAllocation> =
        entityManager.createQuery(
            """
            SELECT allocation FROM InformationRequestNoticeSequenceAllocation allocation
            WHERE allocation.outboundNoticeId = :noticeId
            ORDER BY allocation.sequenceKey
            """.trimIndent(),
            InformationRequestNoticeSequenceAllocation::class.java,
        )
            .setParameter("noticeId", noticeId)
            .resultList
}

@ApplicationScoped
class InformationRequestNoticeRenderFailureRepository :
    BaseRepository<InformationRequestNoticeRenderFailure>(InformationRequestNoticeRenderFailure::class.java) {
    fun record(intentId: UUID, requestId: UUID, failureCode: String, at: Timestamp): Boolean =
        entityManager.createNativeQuery(
            """
            INSERT INTO information_request_notice_render_failure
                (id, notice_intent_id, information_request_id, attempt_number, failure_code, failed_at)
            SELECT :id, :intent, :request, COALESCE(MAX(failure.attempt_number), 0) + 1, :code, :at
            FROM information_request_notice_render_failure failure
            WHERE failure.notice_intent_id = :intent
            ON CONFLICT (notice_intent_id, attempt_number) DO NOTHING
            """.trimIndent(),
        )
            .setParameter("id", UUID.randomUUID())
            .setParameter("intent", intentId)
            .setParameter("request", requestId)
            .setParameter("code", failureCode)
            .setParameter("at", at)
            .executeUpdate() == 1

    fun countsForRequests(requestIds: Collection<UUID>): Map<UUID, Int> {
        if (requestIds.isEmpty()) return emptyMap()
        return entityManager.createQuery(
            """
            SELECT failure.noticeIntentId, COUNT(failure)
            FROM InformationRequestNoticeRenderFailure failure
            WHERE failure.informationRequestId IN :requestIds
            GROUP BY failure.noticeIntentId
            """.trimIndent(),
            Array<Any>::class.java,
        )
            .setParameter("requestIds", requestIds)
            .resultList
            .associate { row -> row[0] as UUID to (row[1] as Number).toInt() }
    }
}

@ApplicationScoped
class InformationRequestNoticeDeliveryAttemptRepository :
    BaseRepository<InformationRequestNoticeDeliveryAttempt>(InformationRequestNoticeDeliveryAttempt::class.java)
{
    fun findForNotice(noticeId: UUID): List<InformationRequestNoticeDeliveryAttempt> =
        entityManager.createQuery(
            """
            SELECT attempt FROM InformationRequestNoticeDeliveryAttempt attempt
            WHERE attempt.outboundNoticeId = :noticeId
            ORDER BY attempt.attemptNumber
            """.trimIndent(),
            InformationRequestNoticeDeliveryAttempt::class.java,
        )
            .setParameter("noticeId", noticeId)
            .resultList

    fun findForRequest(requestId: UUID): List<InformationRequestNoticeDeliveryAttempt> =
        entityManager.createQuery(
            """
            SELECT attempt FROM InformationRequestNoticeDeliveryAttempt attempt
            WHERE attempt.informationRequestId = :requestId
            ORDER BY attempt.attemptedAt, attempt.attemptNumber
            """.trimIndent(),
            InformationRequestNoticeDeliveryAttempt::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findForRequests(requestIds: Collection<UUID>): List<InformationRequestNoticeDeliveryAttempt>
    {
        if (requestIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "SELECT attempt FROM InformationRequestNoticeDeliveryAttempt attempt WHERE attempt.informationRequestId IN :requestIds",
            InformationRequestNoticeDeliveryAttempt::class.java,
        )
            .setParameter("requestIds", requestIds)
            .resultList
    }
}
