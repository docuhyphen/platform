package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.exchange.ExchangeLifecycleActor
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScopeResolver
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class ExchangeLifecycleAudit @Inject constructor(
    private val auditRecorder: AuditRecorder,
    private val auditOwnerScopeResolver: AuditOwnerScopeResolver,
) {
    fun recordTransition(
        exchangeId: UUID,
        exchangeName: String?,
        previousStatus: ExchangeStatus,
        newStatus: ExchangeStatus,
        actor: ExchangeLifecycleActor,
    ) {
        val eventType = when (newStatus) {
            ExchangeStatus.ACCEPTED_STARTED -> AuditEventType.EXCHANGE_ACCEPTED
            ExchangeStatus.REJECTED -> AuditEventType.EXCHANGE_REJECTED
            ExchangeStatus.ENDED -> AuditEventType.EXCHANGE_ENDED
            ExchangeStatus.RESCINDED -> AuditEventType.EXCHANGE_RESCINDED
            ExchangeStatus.INITIATED -> throw IllegalArgumentException("No transition returns an Exchange to INITIATED")
        }
        try {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = eventType.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actor.actorId,
                    actorKind = actor.actorKind,
                    targetType = ResourceType.EXCHANGE.name,
                    targetId = exchangeId.toString(),
                    targetLabel = exchangeName,
                    owner = auditOwnerScopeResolver.resolve(ResourceType.EXCHANGE, exchangeId),
                    payload = mapOf(
                        "previousStatus" to previousStatus.name,
                        "newStatus" to newStatus.name,
                    ),
                )
            )
        } catch (e: AuditDraftInvalidException) {
            logger.warn("Exchange lifecycle audit draft {} was rejected: {}", eventType.key, e.message)
        }
    }

    fun recordEndingRefused(
        exchangeId: UUID,
        exchangeName: String?,
        reasonCode: String,
        refusedRequestCount: Int,
        workflowInstanceId: UUID?,
    ) {
        try {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = AuditEventType.EXCHANGE_ENDED.key,
                    outcome = AuditOutcome.FAILURE,
                    actorKind = AuditActorKind.WORKFLOW,
                    targetType = ResourceType.EXCHANGE.name,
                    targetId = exchangeId.toString(),
                    targetLabel = exchangeName,
                    owner = auditOwnerScopeResolver.resolve(ResourceType.EXCHANGE, exchangeId),
                    payload = mapOf(
                        "reasonCode" to reasonCode,
                        "refusedRequestCount" to refusedRequestCount.toString(),
                    ),
                    idempotencyKey = workflowInstanceId?.let { "$ENDING_REFUSED_KEY_PREFIX$it" },
                )
            )
        } catch (e: AuditDraftInvalidException) {
            logger.warn("Exchange ending refusal audit draft was rejected: {}", e.message)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ExchangeLifecycleAudit::class.java)
        const val ENDING_REFUSED_KEY_PREFIX = "exchange.ending.refused:"
    }
}
