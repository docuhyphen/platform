package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchangeState
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorKind
import com.docuhyphen.app.api.model.entity.InformationRequestDiscrepancyResolution
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueSource
import com.docuhyphen.app.api.model.entity.InformationRequestSourceConfidence
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReconciliationOutcome
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestConnectorExchangeDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val connectorKey: String,
    val connectorKind: InformationRequestConnectorKind,
    val contractVersion: Int,
    val state: InformationRequestConnectorExchangeState,
    val lookupReference: String? = null,
    val externalReference: String? = null,
    val attemptCount: Int,
    @Serializable(with = TimestampSerializer::class) val nextAttemptAt: Timestamp? = null,
    val failureCode: String? = null,
    @Serializable(with = TimestampSerializer::class) val requestedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val completedAt: Timestamp? = null,
    val requestedByCaller: Boolean,
)

@Serializable
data class InformationRequestImportedValueDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val sourceKind: InformationRequestImportedValueSource,
    @Serializable(with = UUIDSerializer::class) val connectorExchangeId: UUID? = null,
    val sourceReference: String,
    val resultKey: String,
    val valueType: FieldValueType,
    val value: JsonElement,
    val confidence: InformationRequestSourceConfidence,
    @Serializable(with = TimestampSerializer::class) val verifiedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    val provenanceReference: String,
    @Serializable(with = TimestampSerializer::class) val recordedAt: Timestamp,
    val recordedByCaller: Boolean,
    val decision: InformationRequestImportedValueDecisionDto? = null,
    val discrepancies: List<InformationRequestImportedValueDiscrepancyDto>,
)

@Serializable
data class InformationRequestImportedValueDecisionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val decision: InformationRequestImportedValueDecisionKind,
    val reasonCode: String,
    @Serializable(with = TimestampSerializer::class) val decidedAt: Timestamp,
    val decidedByCaller: Boolean,
)

@Serializable
data class InformationRequestImportedValueDiscrepancyDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val importedValueId: UUID,
    val responseRevision: Long,
    val importedValue: JsonElement,
    val responseValue: JsonElement,
    @Serializable(with = TimestampSerializer::class) val recordedAt: Timestamp,
    val resolution: InformationRequestDiscrepancyResolutionDto? = null,
)

@Serializable
data class InformationRequestDiscrepancyResolutionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val resolution: InformationRequestDiscrepancyResolution,
    val reasonCode: String,
    @Serializable(with = TimestampSerializer::class) val resolvedAt: Timestamp,
    val resolvedByCaller: Boolean,
)

@Serializable
data class InformationRequestImportedValueReconciliationDto(
    @Serializable(with = UUIDSerializer::class) val importedValueId: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val outcome: InformationRequestReconciliationOutcome,
    val responseValue: JsonElement? = null,
    @Serializable(with = UUIDSerializer::class) val discrepancyId: UUID? = null,
)

@Serializable
data class InformationRequestGeneratedOutputDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val packageId: UUID? = null,
    val outputKey: String,
    val externalReference: String,
    val contentHashSha256: String? = null,
    val mediaType: String? = null,
    val producedBySource: String,
    @Serializable(with = TimestampSerializer::class) val producedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val recordedAt: Timestamp,
    val recordedByCaller: Boolean,
)
