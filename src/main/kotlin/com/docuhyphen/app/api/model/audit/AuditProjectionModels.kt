package com.docuhyphen.app.api.model.audit

import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.time.Instant
import java.util.*

data class AuditProjectionCursor(
    val occurredAt: Instant,
    val eventId: UUID,
)

data class AuditProjectionEvent(
    val eventId: UUID,
    val category: String,
    val eventTypeKey: String,
    val outcome: String,
    val occurredAt: Instant,
    val recordedAt: Instant,
    val ledgerTime: Instant,
    val streamId: String,
    val streamSequence: Long,
    val actorKind: String,
    val actorId: UUID?,
    val actorRole: String?,
    val actorLabel: String?,
    val ownerType: String,
    val ownerId: UUID?,
    val organizationId: UUID?,
    val organizationLabel: String?,
    val targetType: String?,
    val targetId: String?,
    val targetLabel: String?,
    val reason: String?,
    val payload: Map<String, String>,
    val eventHash: String,
    val prevHash: String?,
)

data class AuditProjectionPage(
    val items: List<AuditProjectionEvent>,
    val nextCursor: AuditProjectionCursor?,
)

data class AuditAccessActor(
    val principal: PrincipalRef,
    val context: AuthorizationContext,
    val capabilities: Set<Capability>,
)
