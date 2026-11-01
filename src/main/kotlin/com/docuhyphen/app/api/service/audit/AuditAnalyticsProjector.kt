package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.AnalyticsProjectionResult
import com.docuhyphen.app.api.model.entity.AuditAnalyticsFact
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.audit.AuditAnalyticsFactRepository
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

@ApplicationScoped
class AuditAnalyticsProjector @Inject constructor(
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
    private val auditAnalyticsFactRepository: AuditAnalyticsFactRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditAnalyticsProjector::class.java)
    }

    fun project(batchSize: Int = 500): AnalyticsProjectionResult
    {
        val candidates = auditLedgerEventRepository.findUnprojected(batchSize)
        var projected = 0
        var alreadyProjected = 0
        var failed = 0

        for (event in candidates)
        {
            try
            {
                if (projectOne(event))
                {
                    projected++
                }
                else
                {
                    alreadyProjected++
                }
            }
            catch (e: Exception)
            {
                logger.error(
                    "AuditAnalyticsProjector failed to project ledgerEventId={}: {}",
                    event.eventId,
                    e.message,
                    e
                )
                failed++
            }
        }

        return AnalyticsProjectionResult(projected, alreadyProjected, failed)
    }

    @Transactional
    fun rebuild(organizationId: UUID?, platformOnly: Boolean): AnalyticsProjectionResult
    {
        auditAnalyticsFactRepository.deleteAllByOrganization(organizationId, platformOnly)
        var projected = 0
        var failed = 0
        for (event in auditLedgerEventRepository.findAllByOrganization(organizationId, platformOnly))
        {
            try
            {
                if (projectOne(event)) projected++
            }
            catch (e: Exception)
            {
                logger.error(
                    "AuditAnalyticsProjector rebuild failed for ledgerEventId={}: {}",
                    event.eventId,
                    e.message,
                    e
                )
                failed++
            }
        }
        return AnalyticsProjectionResult(projected, 0, failed)
    }

    @Transactional
    open fun projectOne(event: AuditLedgerEvent): Boolean
    {
        if (auditAnalyticsFactRepository.existsByLedgerEventId(event.eventId))
        {
            return false
        }

        val fact = AuditAnalyticsFact().apply {
            ledgerEventId = event.eventId
            organizationId = event.organizationId
            ownerType = event.ownerType
            ownerId = event.ownerId
            streamId = event.streamId
            category = event.category
            eventTypeKey = event.eventTypeKey
            outcome = event.outcome
            actorKind = event.actorKind
            occurredAt = event.occurredAt
            occurredDate = event.occurredAt.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
            schemaVersion = event.schemaVersion
            projectedAt = Timestamp.from(Instant.now())
        }
        auditAnalyticsFactRepository.insert(fact)
        return true
    }
}
