package com.docuhyphen.app.api.model.informationrequest.audit

import com.docuhyphen.app.api.model.audit.AuditTargetRecord
import com.docuhyphen.app.api.model.audit.DEFAULT_AUDIT_TARGET_LIMIT
import com.docuhyphen.app.api.model.entity.InformationRequestRecordExport
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import java.time.Instant
import java.util.*

data class InformationRequestAuditSearch(
    val requestId: UUID? = null,
    val eventClass: String? = null,
    val eventTypeKey: String? = null,
    val actorId: UUID? = null,
    val occurredAfter: Instant? = null,
    val occurredBefore: Instant? = null,
    val limit: Int = DEFAULT_AUDIT_TARGET_LIMIT,
    val offset: Int = 0,
)

data class InformationRequestAuditEvent(
    val record: AuditTargetRecord,
    val eventClass: String,
    val payload: Map<String, String>,
    val withheldKeyCount: Int,
)

data class InformationRequestAuditPage(
    val events: List<InformationRequestAuditEvent>,
    val total: Long,
    val limit: Int,
    val offset: Int,
)

data class InformationRequestReconciliationGap(
    val transitionId: UUID,
    val sequenceNumber: Int,
    val mutation: String,
    val expectedEventTypeKey: String,
)

data class InformationRequestReconciliationStray(
    val eventId: UUID,
    val eventTypeKey: String,
    val businessTransactionId: String?,
)

data class InformationRequestAuditReconciliation(
    val requestId: UUID,
    val auditedTransitionCount: Int,
    val matchedTransitionCount: Int,
    val missing: List<InformationRequestReconciliationGap>,
    val unmatched: List<InformationRequestReconciliationStray>,
    val unsealedEventCount: Int,
)
{
    val reconciled: Boolean get() = missing.isEmpty() && unmatched.isEmpty()
}

data class CreateInformationRequestRecordExportCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val transferRegion: String?,
    val idempotencyKey: String,
)

data class InformationRequestRecordExportView(
    val export: InformationRequestRecordExport,
    val sourceRequestIds: List<UUID>,
    val verified: Boolean,
    val content: String?,
)
