package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuditEventCursorDto(
    val occurredAt: String,
    val eventId: String,
)

@Serializable
data class AuditEventDto(
    val eventId: String,
    val category: String,
    val eventTypeKey: String,
    val outcome: String,
    val occurredAt: String,
    val recordedAt: String,
    val ledgerTime: String,
    val streamId: String,
    val streamSequence: Long,
    val actorKind: String,
    val actorId: String? = null,
    val actorRole: String? = null,
    val actorLabel: String? = null,
    val ownerType: String,
    val ownerId: String? = null,
    val organizationId: String? = null,
    val organizationLabel: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val targetLabel: String? = null,
    val reason: String? = null,
    val payload: Map<String, String> = emptyMap(),
    val eventHash: String,
    val prevHash: String? = null,
)

@Serializable
data class AuditEventPageDto(
    val items: List<AuditEventDto>,
    val nextCursor: AuditEventCursorDto? = null,
)
