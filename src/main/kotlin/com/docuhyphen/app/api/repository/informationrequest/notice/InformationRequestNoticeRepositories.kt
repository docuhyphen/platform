package com.docuhyphen.app.api.repository.informationrequest.notice

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
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

    fun findAwaitingDeliveryIds(maximumAttempts: Int, limit: Int, requestId: UUID? = null): List<UUID>
    {
        val requestClause = if (requestId != null) "AND notice.informationRequestId = :requestId" else ""
        val query = entityManager.createQuery(
            """
            SELECT notice.id FROM InformationRequestOutboundNotice notice
            WHERE notice.endpointState = :resolved
              $requestClause
              AND NOT EXISTS (
                SELECT 1 FROM InformationRequestNoticeDeliveryAttempt settled
                WHERE settled.outboundNoticeId = notice.id AND settled.outcome <> :failed
              )
              AND (SELECT COUNT(attempt) FROM InformationRequestNoticeDeliveryAttempt attempt
                   WHERE attempt.outboundNoticeId = notice.id) < :maximumAttempts
            ORDER BY notice.renderedAt, notice.id
            """.trimIndent(),
            UUID::class.java,
        )
            .setParameter("resolved", InformationRequestNoticeEndpointState.RESOLVED)
            .setParameter("failed", InformationRequestNoticeAttemptOutcome.FAILED)
            .setParameter("maximumAttempts", maximumAttempts.toLong())
            .setMaxResults(limit)
        requestId?.let { query.setParameter("requestId", it) }
        return query.resultList
    }

    fun lock(noticeId: UUID): InformationRequestOutboundNotice?
    {
        val notice = entityManager.find(InformationRequestOutboundNotice::class.java, noticeId) ?: return null
        entityManager.lock(notice, LockModeType.PESSIMISTIC_WRITE)
        return notice
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
