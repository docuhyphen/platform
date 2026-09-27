package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestRecordExportKind
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RecordTransferDecision
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestAuditEventDto(
    @Serializable(with = UUIDSerializer::class) val eventId: UUID,
    val eventTypeKey: String,
    val eventClass: String,
    val category: String,
    val outcome: String,
    @Serializable(with = TimestampSerializer::class) val occurredAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val recordedAt: Timestamp,
    val actorKind: String? = null,
    @Serializable(with = UUIDSerializer::class) val actorId: UUID? = null,
    val requestId: String? = null,
    val businessTransactionId: String? = null,
    val sealed: Boolean,
    val eventHash: String? = null,
    val streamSequence: Long? = null,
    val payload: Map<String, String>,
    val withheldKeyCount: Int,
)

@Serializable
data class InformationRequestAuditPageDto(
    val items: List<InformationRequestAuditEventDto>,
    val total: Long,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class InformationRequestReconciliationGapDto(
    @Serializable(with = UUIDSerializer::class) val transitionId: UUID,
    val sequenceNumber: Int,
    val mutation: String,
    val expectedEventTypeKey: String,
)

@Serializable
data class InformationRequestReconciliationStrayDto(
    @Serializable(with = UUIDSerializer::class) val eventId: UUID,
    val eventTypeKey: String,
    val businessTransactionId: String? = null,
)

@Serializable
data class InformationRequestAuditReconciliationDto(
    @Serializable(with = UUIDSerializer::class) val requestId: UUID,
    val reconciled: Boolean,
    val auditedTransitionCount: Int,
    val matchedTransitionCount: Int,
    val unsealedEventCount: Int,
    val missing: List<InformationRequestReconciliationGapDto>,
    val unmatched: List<InformationRequestReconciliationStrayDto>,
)

@Serializable
data class InformationRequestRecordExportDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val exportKind: InformationRequestRecordExportKind,
    @Serializable(with = UUIDSerializer::class) val requestId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val subjectIdentityRefId: UUID? = null,
    val sourceRequestIds: List<@Serializable(with = UUIDSerializer::class) UUID>,
    val schemaVersion: Int,
    val contentHashAlgorithm: String,
    val contentHash: String,
    val contentLength: Long,
    val storageLocation: String,
    val transferRegion: String? = null,
    val transferDecision: RecordTransferDecision,
    val requestedByPrincipalKind: PrincipalKind,
    @Serializable(with = UUIDSerializer::class) val requestedByPrincipalId: UUID,
    @Serializable(with = TimestampSerializer::class) val requestedAt: Timestamp,
    val verified: Boolean,
    val content: JsonElement? = null,
)
