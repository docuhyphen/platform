package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchange
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchangeState
import com.docuhyphen.app.api.model.entity.InformationRequestGeneratedOutput
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValue
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecision
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDiscrepancy
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDiscrepancyResolution
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Timestamp
import java.util.UUID

@ApplicationScoped
class InformationRequestConnectorExchangeRepository :
    BaseRepository<InformationRequestConnectorExchange>(InformationRequestConnectorExchange::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestConnectorExchange> =
        entityManager.createQuery(
            "SELECT exchange FROM InformationRequestConnectorExchange exchange WHERE exchange.informationRequestId = :requestId ORDER BY exchange.requestedAt, exchange.id",
            InformationRequestConnectorExchange::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findDueIds(now: Timestamp, limit: Int): List<UUID> =
        entityManager.createQuery(
            """
            SELECT exchange.id
            FROM InformationRequestConnectorExchange exchange
            WHERE exchange.state IN :open
              AND (exchange.nextAttemptAt IS NULL OR exchange.nextAttemptAt <= :now)
            ORDER BY exchange.requestedAt, exchange.id
            """.trimIndent(),
            UUID::class.java,
        )
            .setParameter("open", listOf(InformationRequestConnectorExchangeState.REQUESTED, InformationRequestConnectorExchangeState.PENDING))
            .setParameter("now", now)
            .setMaxResults(limit)
            .resultList
}

@ApplicationScoped
class InformationRequestImportedValueRepository :
    BaseRepository<InformationRequestImportedValue>(InformationRequestImportedValue::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestImportedValue> =
        entityManager.createQuery(
            "SELECT value FROM InformationRequestImportedValue value WHERE value.informationRequestId = :requestId ORDER BY value.recordedAt, value.id",
            InformationRequestImportedValue::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}

@ApplicationScoped
class InformationRequestImportedValueDecisionRepository :
    BaseRepository<InformationRequestImportedValueDecision>(InformationRequestImportedValueDecision::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestImportedValueDecision> =
        entityManager.createQuery(
            "SELECT decision FROM InformationRequestImportedValueDecision decision WHERE decision.informationRequestId = :requestId",
            InformationRequestImportedValueDecision::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}

@ApplicationScoped
class InformationRequestImportedValueDiscrepancyRepository :
    BaseRepository<InformationRequestImportedValueDiscrepancy>(InformationRequestImportedValueDiscrepancy::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestImportedValueDiscrepancy> =
        entityManager.createQuery(
            "SELECT discrepancy FROM InformationRequestImportedValueDiscrepancy discrepancy WHERE discrepancy.informationRequestId = :requestId ORDER BY discrepancy.recordedAt, discrepancy.id",
            InformationRequestImportedValueDiscrepancy::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}

@ApplicationScoped
class InformationRequestImportedValueDiscrepancyResolutionRepository :
    BaseRepository<InformationRequestImportedValueDiscrepancyResolution>(InformationRequestImportedValueDiscrepancyResolution::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestImportedValueDiscrepancyResolution> =
        entityManager.createQuery(
            "SELECT resolution FROM InformationRequestImportedValueDiscrepancyResolution resolution WHERE resolution.informationRequestId = :requestId",
            InformationRequestImportedValueDiscrepancyResolution::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}

@ApplicationScoped
class InformationRequestGeneratedOutputRepository :
    BaseRepository<InformationRequestGeneratedOutput>(InformationRequestGeneratedOutput::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestGeneratedOutput> =
        entityManager.createQuery(
            "SELECT output FROM InformationRequestGeneratedOutput output WHERE output.informationRequestId = :requestId ORDER BY output.recordedAt, output.id",
            InformationRequestGeneratedOutput::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}
