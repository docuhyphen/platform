package com.docuhyphen.app.api.repository.informationrequest.oversight

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.time.Instant

@ApplicationScoped
class InformationRequestHealthRepository @Inject constructor(
    private val entityManager: EntityManager,
)
{
    fun countIssuedWithoutExecutionGrant(): Long =
        count(
            """
            SELECT COUNT(*) FROM information_request request
            WHERE request.issued_at IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM request_execution_grant grant_row WHERE grant_row.request_id = request.id)
            """,
        )

    fun countReservationsAboveCap(): Long =
        count(
            """
            SELECT COUNT(*) FROM (
                SELECT grant_row.id
                FROM request_execution_grant grant_row
                JOIN request_execution_usage_reservation reservation ON reservation.grant_id = grant_row.id
                WHERE reservation.status IN ('RESERVED', 'CONSUMED')
                  AND reservation.usage_kind = 'ACTING_PARTY'
                  AND grant_row.acting_party_cap IS NOT NULL
                GROUP BY grant_row.id, grant_row.acting_party_cap
                HAVING SUM(reservation.quantity) > grant_row.acting_party_cap
            ) above_cap
            """,
        )

    fun countNoticeIntentsWithoutNoticeBefore(cutoff: Instant): Long =
        count(
            """
            SELECT COUNT(*) FROM information_request_notice_intent intent
            WHERE intent.created_at < :cutoff
              AND NOT EXISTS (
                  SELECT 1 FROM information_request_outbound_notice notice WHERE notice.notice_intent_id = intent.id
              )
            """,
            cutoff,
        )

    fun countConnectorExchangesFailedSince(cutoff: Instant, failureCodes: Collection<String>): Long =
        entityManager.createNativeQuery(
            """
            SELECT COUNT(*) FROM information_request_connector_exchange exchange_row
            WHERE exchange_row.state = 'FAILED'
              AND exchange_row.completed_at >= :cutoff
              AND exchange_row.failure_code IN (:codes)
            """.trimIndent(),
        )
            .setParameter("cutoff", java.sql.Timestamp.from(cutoff))
            .setParameter("codes", failureCodes)
            .singleResult.let { (it as Number).toLong() }

    fun countDisposalClaimsClaimedBefore(cutoff: Instant): Long =
        count(
            """
            SELECT COUNT(*) FROM record_disposal_claim claim
            WHERE claim.state IN ('CLAIMED', 'OBJECTS_DELETED')
              AND claim.claimed_at < :cutoff
            """,
            cutoff,
        )

    fun countPendingRequestEventsBefore(cutoff: Instant): Long =
        count(
            """
            SELECT COUNT(*) FROM workflow_event_outbox event_row
            WHERE event_row.status = 'PENDING'
              AND event_row.event_type LIKE 'information_request.%'
              AND event_row.created_at < :cutoff
            """,
            cutoff,
        )

    private fun count(sql: String, cutoff: Instant? = null): Long
    {
        val query = entityManager.createNativeQuery(sql.trimIndent())
        cutoff?.let { query.setParameter("cutoff", java.sql.Timestamp.from(it)) }
        return (query.singleResult as Number).toLong()
    }
}
