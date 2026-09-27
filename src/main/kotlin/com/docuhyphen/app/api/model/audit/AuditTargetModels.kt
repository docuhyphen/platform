package com.docuhyphen.app.api.model.audit

import java.time.Instant
import java.util.UUID

data class AuditTargetQuery(
    val eventTypePrefix: String? = null,
    val eventTypeKeys: Set<String> = emptySet(),
    val targetIds: Set<String> = emptySet(),
    val actorId: UUID? = null,
    val occurredAfter: Instant? = null,
    val occurredBefore: Instant? = null,
    val limit: Int = DEFAULT_AUDIT_TARGET_LIMIT,
    val offset: Int = 0,
    val newestFirst: Boolean = false,
)

data class AuditTargetRecord(
    val eventId: UUID,
    val eventTypeKey: String,
    val category: String,
    val outcome: String,
    val occurredAt: Instant,
    val recordedAt: Instant,
    val actorKind: String?,
    val actorId: UUID?,
    val targetType: String?,
    val targetId: String?,
    val businessTransactionId: String?,
    val payload: Map<String, String>,
    val sealed: Boolean,
    val eventHash: String?,
    val streamSequence: Long?,
)

data class AuditTargetRecordPage(
    val records: List<AuditTargetRecord>,
    val total: Long,
)

const val DEFAULT_AUDIT_TARGET_LIMIT = 100
const val MAXIMUM_AUDIT_TARGET_LIMIT = 500
