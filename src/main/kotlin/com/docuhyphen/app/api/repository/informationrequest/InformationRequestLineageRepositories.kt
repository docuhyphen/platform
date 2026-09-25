package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestCarryForward
import com.docuhyphen.app.api.model.entity.InformationRequestLineage
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRefreshRule
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

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
}
