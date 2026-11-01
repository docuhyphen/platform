package com.docuhyphen.app.api.model.audit

import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import java.util.*

sealed interface AuditOwnerScope
{
    data object Platform : AuditOwnerScope
    data class Organization(val organizationId: UUID) : AuditOwnerScope
    data class Personal(val userId: UUID) : AuditOwnerScope
}

data class AuditEventDraft(
    val owner: AuditOwnerScope,
    val eventTypeKey: String,
    val outcome: AuditOutcome,
    val actorId: UUID? = null,
    val actorRole: String? = null,
    val actorKind: AuditActorKind? = null,
    val actorLabel: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val targetLabel: String? = null,
    val organizationLabel: String? = null,
    val sessionId: String? = null,
    val reason: String? = null,
    val payload: Map<String, String> = emptyMap(),
    val eventId: UUID = UUID.randomUUID(),
    val idempotencyKey: String? = null,
    val businessTransactionId: String? = null,
)
