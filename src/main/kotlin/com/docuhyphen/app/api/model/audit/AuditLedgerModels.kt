package com.docuhyphen.app.api.model.audit

import kotlinx.serialization.Serializable

data class LedgerDrainResult(val appended: Int, val alreadyLedgered: Int, val failed: Int)

@Serializable
data class CanonicalLedgerEnvelope(
    val eventId: String,
    val eventTypeKey: String,
    val category: String,
    val outcome: String,
    val schemaVersion: Int,
    val occurredAt: String,
    val recordedAt: String,
    val streamId: String,
    val actorKind: String,
    val actorId: String?,
    val actorRole: String?,
    val actorLabel: String?,
    val sessionId: String?,
    val serverTraceId: String?,
    val correlationId: String?,
    val causationId: String?,
    val organizationId: String?,
    val organizationLabel: String?,
    val targetType: String?,
    val targetId: String?,
    val targetLabel: String?,
    val reason: String?,
    val payloadJson: String,
)
