package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.AuditTargetQuery
import com.docuhyphen.app.api.model.audit.AuditTargetRecord
import com.docuhyphen.app.api.model.audit.AuditTargetRecordPage
import com.docuhyphen.app.api.model.entity.AuditOutboxEntry
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.repository.audit.AuditOutboxRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@ApplicationScoped
class AuditTargetHistoryService @Inject constructor(
    private val outboxRepository: AuditOutboxRepository,
    private val ledgerRepository: AuditLedgerEventRepository,
)
{
    fun recordsForTarget(targetType: String, targetId: String, query: AuditTargetQuery): AuditTargetRecordPage =
        page(
            outboxRepository.findForTargets(null, targetType, setOf(targetId), query),
            outboxRepository.countForTargets(null, targetType, setOf(targetId), query),
        )

    fun recordsForOwner(owner: AuditOwnerScope, targetType: String, query: AuditTargetQuery): AuditTargetRecordPage =
        page(
            outboxRepository.findForTargets(owner, targetType, query.targetIds, query),
            outboxRepository.countForTargets(owner, targetType, query.targetIds, query),
        )

    private fun page(entries: List<AuditOutboxEntry>, total: Long): AuditTargetRecordPage
    {
        val sealed = ledgerRepository.findByEventIds(entries.map { it.eventId }).associateBy { it.eventId }
        return AuditTargetRecordPage(
            records = entries.map { entry ->
                val ledgered = sealed[entry.eventId]
                AuditTargetRecord(
                    eventId = entry.eventId,
                    eventTypeKey = entry.eventTypeKey,
                    category = entry.category,
                    outcome = entry.outcome,
                    occurredAt = entry.occurredAt.toInstant(),
                    recordedAt = entry.recordedAt.toInstant(),
                    actorKind = entry.actorKind,
                    actorId = entry.actorId,
                    targetType = entry.targetType,
                    targetId = entry.targetId,
                    businessTransactionId = entry.businessTransactionId,
                    payload = runCatching { Json.decodeFromString(PAYLOAD, entry.payloadJson) }.getOrDefault(emptyMap()),
                    sealed = ledgered != null,
                    eventHash = ledgered?.eventHash,
                    streamSequence = ledgered?.streamSequence,
                )
            },
            total = total,
        )
    }

    private companion object
    {
        val PAYLOAD = MapSerializer(String.serializer(), String.serializer())
    }
}
