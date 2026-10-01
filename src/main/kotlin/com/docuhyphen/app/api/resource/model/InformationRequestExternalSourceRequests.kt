package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestDiscrepancyResolution
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestSourceConfidence
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.util.*

@Serializable
data class RequestInformationRequestConnectorExchangeRequest(
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val connectorKey: String,
    val lookupReference: String? = null,
)

@Serializable
data class ProposeInformationRequestImportedValueRequest(
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val resultKey: String,
    val valueType: FieldValueType,
    val value: JsonElement,
    val sourceReference: String,
    val confidence: InformationRequestSourceConfidence = InformationRequestSourceConfidence.ASSERTED,
    @Serializable(with = TimestampSerializer::class) val verifiedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    val provenanceReference: String,
)

@Serializable
data class DecideInformationRequestImportedValueRequest(
    val decision: InformationRequestImportedValueDecisionKind,
    val reasonCode: String,
)

@Serializable
data class ResolveInformationRequestDiscrepancyRequest(
    val resolution: InformationRequestDiscrepancyResolution,
    val reasonCode: String,
)

@Serializable
data class RecordInformationRequestGeneratedOutputRequest(
    @Serializable(with = UUIDSerializer::class) val packageId: UUID? = null,
    val outputKey: String,
    val externalReference: String,
    val contentHashSha256: String? = null,
    val mediaType: String? = null,
    val producedBySource: String,
    @Serializable(with = TimestampSerializer::class) val producedAt: Timestamp,
)
