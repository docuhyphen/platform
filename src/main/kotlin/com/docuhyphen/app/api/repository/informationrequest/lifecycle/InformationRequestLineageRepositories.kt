package com.docuhyphen.app.api.repository.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestRefreshSource
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.*

@ApplicationScoped
class InformationRequestLineageRepository :
    BaseRepository<InformationRequestLineage>(InformationRequestLineage::class.java)
{
    fun findForSuccessor(successorRequestId: UUID): InformationRequestLineage? =
        entityManager.createQuery(
            """
            SELECT lineage
            FROM InformationRequestLineage lineage
            WHERE lineage.successorRequestId = :requestId
            """.trimIndent(),
            InformationRequestLineage::class.java,
        )
            .setParameter("requestId", successorRequestId)
            .resultList
            .firstOrNull()

    fun findForSource(sourceRequestId: UUID): List<InformationRequestLineage> =
        entityManager.createQuery(
            """
            SELECT lineage
            FROM InformationRequestLineage lineage
            WHERE lineage.sourceRequestId = :requestId
            ORDER BY lineage.createdAt, lineage.id
            """.trimIndent(),
            InformationRequestLineage::class.java,
        )
            .setParameter("requestId", sourceRequestId)
            .resultList

    fun existsRefresh(refreshRuleId: UUID, sourcePackageId: UUID): Boolean =
        entityManager.createQuery(
            """
            SELECT COUNT(lineage)
            FROM InformationRequestLineage lineage
            WHERE lineage.lineageKind = :kind
              AND lineage.refreshRuleId = :refreshRuleId
              AND lineage.sourcePackageId = :sourcePackageId
            """.trimIndent(),
            java.lang.Long::class.java,
        )
            .setParameter("kind", InformationRequestLineageKind.REFRESH)
            .setParameter("refreshRuleId", refreshRuleId)
            .setParameter("sourcePackageId", sourcePackageId)
            .singleResult
            .toLong() > 0

    fun findForRecurrence(recurrenceId: UUID): List<InformationRequestLineage> =
        entityManager.createQuery(
            """
            SELECT lineage
            FROM InformationRequestLineage lineage
            WHERE lineage.recurrenceId = :recurrenceId
            ORDER BY lineage.recurrenceSequence
            """.trimIndent(),
            InformationRequestLineage::class.java,
        )
            .setParameter("recurrenceId", recurrenceId)
            .resultList
}

@ApplicationScoped
class InformationRequestCarryForwardRepository :
    BaseRepository<InformationRequestCarryForward>(InformationRequestCarryForward::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestCarryForward> =
        entityManager.createQuery(
            """
            SELECT carryForward
            FROM InformationRequestCarryForward carryForward
            WHERE carryForward.informationRequestId = :requestId
            ORDER BY carryForward.informationRequestRequirementId
            """.trimIndent(),
            InformationRequestCarryForward::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}

@ApplicationScoped
class InformationRequestRecurrenceRepository :
    BaseRepository<InformationRequestRecurrence>(InformationRequestRecurrence::class.java)
{
    fun findForOrigin(originRequestId: UUID): InformationRequestRecurrence? =
        entityManager.createQuery(
            """
            SELECT recurrence
            FROM InformationRequestRecurrence recurrence
            WHERE recurrence.originRequestId = :requestId
            """.trimIndent(),
            InformationRequestRecurrence::class.java,
        )
            .setParameter("requestId", originRequestId)
            .resultList
            .firstOrNull()
}

@ApplicationScoped
class InformationRequestRefreshRuleRepository :
    BaseRepository<InformationRequestRefreshRule>(InformationRequestRefreshRule::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestRefreshRule> =
        entityManager.createQuery(
            """
            SELECT rule
            FROM InformationRequestRefreshRule rule
            WHERE rule.informationRequestId = :requestId
            ORDER BY rule.requirementKey
            """.trimIndent(),
            InformationRequestRefreshRule::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findRequirementIdsByKey(requestId: UUID): Map<String, List<UUID>> =
        entityManager.createNativeQuery(
            """
            SELECT template_requirement.requirement_key, requirement.id
            FROM information_request_requirement requirement
                     JOIN information_request request
                          ON request.id = requirement.information_request_id
                              AND request.template_version_id = requirement.source_template_version_id
                     JOIN information_request_template_requirement template_requirement
                          ON template_requirement.id = requirement.source_template_requirement_id
            WHERE requirement.information_request_id = :requestId
            """.trimIndent(),
        )
            .setParameter("requestId", requestId)
            .resultList
            .map { it as Array<*> }
            .groupBy({ it[0] as String }, { it[1] as UUID })

    fun findRefreshSource(requestId: UUID, requirementKey: String): InformationRequestRefreshSource? =
        entityManager.createNativeQuery(
            """
            SELECT submitted.id, MIN(evidence_version.expires_on)
            FROM information_request_submission_package submitted
                     JOIN information_request_submission_item item
                          ON item.package_id = submitted.id AND item.requirement_key = :requirementKey
                     JOIN information_request_submission_evidence member ON member.item_id = item.id
                     JOIN information_request_evidence_version evidence_version
                          ON evidence_version.id = member.evidence_version_id
            WHERE submitted.information_request_id = :requestId
              AND evidence_version.expires_on IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM information_request_submission_withdrawal withdrawal
                              WHERE withdrawal.package_id = submitted.id)
              AND NOT EXISTS (SELECT 1 FROM information_request_submission_package follower
                              WHERE follower.previous_package_id = submitted.id)
            GROUP BY submitted.id, submitted.package_number
            ORDER BY submitted.package_number DESC
            LIMIT 1
            """.trimIndent(),
        )
            .setParameter("requestId", requestId)
            .setParameter("requirementKey", requirementKey)
            .resultList
            .firstOrNull()
            ?.let { row ->
                val columns = row as Array<*>
                InformationRequestRefreshSource(columns[0] as UUID, localDateOf(requireNotNull(columns[1])))
            }

    fun findDueRuleIds(
        today: LocalDate,
        now: Instant,
        limit: Int,
        sourceStates: Set<InformationRequestState>,
        parentStatuses: Set<ExchangeStatus>,
    ): List<UUID> =
        entityManager.createNativeQuery(
            """
            SELECT rule.id
            FROM information_request_refresh_rule rule
                     JOIN information_request request ON request.id = rule.information_request_id
                     JOIN exchange parent ON parent.id = request.exchange_id
                     JOIN LATERAL (
                SELECT submitted.id AS package_id, MIN(evidence_version.expires_on) AS expires_on
                FROM information_request_submission_package submitted
                         JOIN information_request_submission_item item
                              ON item.package_id = submitted.id AND item.requirement_key = rule.requirement_key
                         JOIN information_request_submission_evidence member ON member.item_id = item.id
                         JOIN information_request_evidence_version evidence_version
                              ON evidence_version.id = member.evidence_version_id
                WHERE submitted.information_request_id = rule.information_request_id
                  AND evidence_version.expires_on IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM information_request_submission_withdrawal withdrawal
                                  WHERE withdrawal.package_id = submitted.id)
                  AND NOT EXISTS (SELECT 1 FROM information_request_submission_package follower
                                  WHERE follower.previous_package_id = submitted.id)
                GROUP BY submitted.id, submitted.package_number
                ORDER BY submitted.package_number DESC
                LIMIT 1
                ) source ON TRUE
            WHERE source.expires_on - CAST(:today AS DATE) <= rule.lead_days
              AND (rule.next_attempt_at IS NULL OR rule.next_attempt_at <= :now)
              AND request.state IN (:sourceStates)
              AND parent.status IN (:parentStatuses)
              AND NOT parent.is_deleted
              AND NOT EXISTS (SELECT 1 FROM information_request_lineage lineage
                              WHERE lineage.lineage_kind = 'REFRESH'
                                AND lineage.refresh_rule_id = rule.id
                                AND lineage.source_package_id = source.package_id)
            ORDER BY rule.next_attempt_at NULLS FIRST, rule.created_at, rule.id
            LIMIT :limit
            """.trimIndent(),
        )
            .setParameter("today", today)
            .setParameter("now", Timestamp.from(now))
            .setParameter("limit", limit)
            .setParameter("sourceStates", sourceStates.map { it.name })
            .setParameter("parentStatuses", parentStatuses.map { it.name })
            .resultList
            .map { it as UUID }

    fun clearRetry(rule: InformationRequestRefreshRule) {
        if (rule.attemptCount == 0 && rule.nextAttemptAt == null) return
        rule.attemptCount = 0
        rule.nextAttemptAt = null
        update(rule)
    }

    private fun localDateOf(value: Any): LocalDate = when (value) {
        is LocalDate -> value
        is java.sql.Date -> value.toLocalDate()
        else -> LocalDate.parse(value.toString())
    }
}

@ApplicationScoped
class InformationRequestLineageSourcePackageRepository :
    BaseRepository<InformationRequestLineageSourcePackage>(InformationRequestLineageSourcePackage::class.java) {
    fun findForLineage(lineageId: UUID): List<InformationRequestLineageSourcePackage> =
        entityManager.createQuery(
            "SELECT preserved FROM InformationRequestLineageSourcePackage preserved WHERE preserved.lineageId = :lineageId ORDER BY preserved.createdAt, preserved.id",
            InformationRequestLineageSourcePackage::class.java,
        )
            .setParameter("lineageId", lineageId)
            .resultList
}
