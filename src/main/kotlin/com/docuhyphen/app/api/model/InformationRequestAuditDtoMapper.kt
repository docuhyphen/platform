package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestAuditEventDto
import com.docuhyphen.app.api.model.dto.InformationRequestAuditPageDto
import com.docuhyphen.app.api.model.dto.InformationRequestAuditReconciliationDto
import com.docuhyphen.app.api.model.dto.InformationRequestReconciliationGapDto
import com.docuhyphen.app.api.model.dto.InformationRequestReconciliationStrayDto
import com.docuhyphen.app.api.model.dto.InformationRequestRecordExportDto
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditPage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditReconciliation
import com.docuhyphen.app.api.model.informationrequest.InformationRequestRecordExportView
import kotlinx.serialization.json.Json
import java.sql.Timestamp

object InformationRequestAuditDtoMapper
{
    fun toDto(page: InformationRequestAuditPage) = InformationRequestAuditPageDto(
        items = page.events.map { event ->
            InformationRequestAuditEventDto(
                eventId = event.record.eventId,
                eventTypeKey = event.record.eventTypeKey,
                eventClass = event.eventClass,
                category = event.record.category,
                outcome = event.record.outcome,
                occurredAt = Timestamp.from(event.record.occurredAt),
                recordedAt = Timestamp.from(event.record.recordedAt),
                actorKind = event.record.actorKind,
                actorId = event.record.actorId,
                requestId = event.record.targetId,
                businessTransactionId = event.record.businessTransactionId,
                sealed = event.record.sealed,
                eventHash = event.record.eventHash,
                streamSequence = event.record.streamSequence,
                payload = event.payload,
                withheldKeyCount = event.withheldKeyCount,
            )
        },
        total = page.total,
        limit = page.limit,
        offset = page.offset,
    )

    fun toDto(reconciliation: InformationRequestAuditReconciliation) = InformationRequestAuditReconciliationDto(
        requestId = reconciliation.requestId,
        reconciled = reconciliation.reconciled,
        auditedTransitionCount = reconciliation.auditedTransitionCount,
        matchedTransitionCount = reconciliation.matchedTransitionCount,
        unsealedEventCount = reconciliation.unsealedEventCount,
        missing = reconciliation.missing.map {
            InformationRequestReconciliationGapDto(it.transitionId, it.sequenceNumber, it.mutation, it.expectedEventTypeKey)
        },
        unmatched = reconciliation.unmatched.map {
            InformationRequestReconciliationStrayDto(it.eventId, it.eventTypeKey, it.businessTransactionId)
        },
    )

    fun toDto(view: InformationRequestRecordExportView) = InformationRequestRecordExportDto(
        id = view.export.id,
        exportKind = view.export.exportKind,
        requestId = view.export.informationRequestId,
        subjectIdentityRefId = view.export.subjectIdentityRefId,
        sourceRequestIds = view.sourceRequestIds,
        schemaVersion = view.export.schemaVersion,
        contentHashAlgorithm = view.export.contentHashAlgorithm,
        contentHash = view.export.contentHash,
        contentLength = view.export.contentLength,
        storageLocation = view.export.storageLocation,
        transferRegion = view.export.transferRegion,
        transferDecision = view.export.transferDecision,
        requestedByPrincipalKind = view.export.requestedByPrincipalKind,
        requestedByPrincipalId = view.export.requestedByPrincipalId,
        requestedAt = view.export.requestedAt,
        verified = view.verified,
        content = view.content?.let(Json::parseToJsonElement),
    )
}
