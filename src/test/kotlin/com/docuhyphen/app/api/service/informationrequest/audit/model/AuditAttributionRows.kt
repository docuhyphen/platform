package com.docuhyphen.app.api.service.informationrequest.audit.model

import java.util.*

internal data class RecordedAuditAttribution(
    val eventTypeKey: String,
    val actorKind: String,
    val actorLabel: String?,
    val sessionId: String?,
    val actorId: UUID,
)

internal data class KeyedAuditTransition(
    val mutation: String,
    val idempotencyKey: String?,
    val commandReceiptId: UUID?,
)
