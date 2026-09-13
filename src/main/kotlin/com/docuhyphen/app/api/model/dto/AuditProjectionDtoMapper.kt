package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.service.audit.AuditProjectionCursor
import com.docuhyphen.app.api.service.audit.AuditProjectionEvent
import com.docuhyphen.app.api.service.audit.AuditProjectionPage

object AuditProjectionDtoMapper
{
    fun toPageDto(page: AuditProjectionPage): AuditEventPageDto = AuditEventPageDto(
        items = page.items.map(::toEventDto),
        nextCursor = page.nextCursor?.let(::toCursorDto),
    )

    fun toEventDto(event: AuditProjectionEvent): AuditEventDto = AuditEventDto(
        eventId = event.eventId.toString(),
        category = event.category,
        eventTypeKey = event.eventTypeKey,
        outcome = event.outcome,
        occurredAt = event.occurredAt.toString(),
        recordedAt = event.recordedAt.toString(),
        ledgerTime = event.ledgerTime.toString(),
        streamId = event.streamId,
        streamSequence = event.streamSequence,
        actorKind = event.actorKind,
        actorId = event.actorId?.toString(),
        actorRole = event.actorRole,
        actorLabel = event.actorLabel,
        ownerType = event.ownerType,
        ownerId = event.ownerId?.toString(),
        organizationId = event.organizationId?.toString(),
        organizationLabel = event.organizationLabel,
        targetType = event.targetType,
        targetId = event.targetId,
        targetLabel = event.targetLabel,
        reason = event.reason,
        payload = event.payload,
        eventHash = event.eventHash,
        prevHash = event.prevHash,
    )

    private fun toCursorDto(cursor: AuditProjectionCursor): AuditEventCursorDto = AuditEventCursorDto(
        occurredAt = cursor.occurredAt.toString(),
        eventId = cursor.eventId.toString(),
    )
}
