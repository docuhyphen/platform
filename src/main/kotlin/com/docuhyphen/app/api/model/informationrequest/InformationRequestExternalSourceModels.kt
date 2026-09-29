package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorKind
import com.docuhyphen.app.api.model.entity.InformationRequestDiscrepancyResolution
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValue
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecision
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDiscrepancy
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDiscrepancyResolution
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestSourceConfidence
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import kotlinx.serialization.json.JsonElement
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class InformationRequestConnectorContract(
    val key: String,
    val kind: InformationRequestConnectorKind,
    val contractVersion: Int,
    val resultKeys: Set<String>,
    val maximumResultAge: Duration? = null,
)

data class RequestInformationRequestConnectorExchangeCommand(
    val requestId: UUID,
    val requirementId: UUID,
    val connectorKey: String,
    val lookupReference: String?,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class InformationRequestConnectorCall(
    val exchangeId: UUID,
    val requestId: UUID,
    val requirementId: UUID,
    val connectorKey: String,
    val contractVersion: Int,
    val lookupReference: String?,
    val externalReference: String?,
    val attempt: Int,
)

data class InformationRequestConnectorValue(
    val resultKey: String,
    val valueType: FieldValueType,
    val value: JsonElement,
)

data class InformationRequestConnectorResult(
    val source: String,
    val confidence: InformationRequestSourceConfidence,
    val verifiedAt: Instant?,
    val expiresAt: Instant?,
    val provenanceReference: String,
    val values: List<InformationRequestConnectorValue>,
)

sealed interface InformationRequestConnectorOutcome
{
    data class Pending(val externalReference: String, val retryAfter: Duration) : InformationRequestConnectorOutcome

    data class Completed(val externalReference: String, val result: InformationRequestConnectorResult) : InformationRequestConnectorOutcome

    data class Failed(val reasonCode: String) : InformationRequestConnectorOutcome
}

@Suppress("LongParameterList")
class ProposeInformationRequestImportedValueCommand(
    val requestId: UUID,
    val requirementId: UUID,
    val resultKey: String,
    val valueType: FieldValueType,
    val value: JsonElement,
    val sourceReference: String,
    val confidence: InformationRequestSourceConfidence,
    val verifiedAt: Instant?,
    val expiresAt: Instant?,
    val provenanceReference: String,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class DecideInformationRequestImportedValueCommand(
    val requestId: UUID,
    val importedValueId: UUID,
    val decision: InformationRequestImportedValueDecisionKind,
    val reasonCode: String,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class ReconcileInformationRequestImportedValuesCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class ResolveInformationRequestDiscrepancyCommand(
    val requestId: UUID,
    val discrepancyId: UUID,
    val resolution: InformationRequestDiscrepancyResolution,
    val reasonCode: String,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

@Suppress("LongParameterList")
class RecordInformationRequestGeneratedOutputCommand(
    val requestId: UUID,
    val packageId: UUID?,
    val outputKey: String,
    val externalReference: String,
    val contentHashSha256: String?,
    val mediaType: String?,
    val producedBySource: String,
    val producedAt: Instant,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class InformationRequestImportedValueProvenance(
    val source: String,
    val confidence: InformationRequestSourceConfidence,
    val verifiedAt: Instant?,
    val expiresAt: Instant?,
    val reference: String,
)

data class InformationRequestPreparedValue(
    val resultKey: String,
    val valueType: FieldValueType,
    val canonicalValue: String,
)

data class InformationRequestPreparedConnectorResult(
    val requirement: InformationRequestRequirement,
    val provenance: InformationRequestImportedValueProvenance,
    val values: List<InformationRequestPreparedValue>,
)

enum class InformationRequestReconciliationOutcome
{
    MATCHES,
    DIFFERS,
    NO_ANSWER,
    NOT_COMPARABLE,
    EXPIRED,
}

sealed interface InformationRequestValueComparison
{
    data object NotComparable : InformationRequestValueComparison

    data object NoAnswer : InformationRequestValueComparison

    data object Matches : InformationRequestValueComparison

    data class Differs(val responseCanonicalValue: String) : InformationRequestValueComparison
}

data class InformationRequestImportedValueReconciliation(
    val importedValueId: UUID,
    val requirementId: UUID,
    val outcome: InformationRequestReconciliationOutcome,
    val responseCanonicalValue: String?,
    val discrepancyId: UUID?,
)

data class InformationRequestDiscrepancyView(
    val discrepancy: InformationRequestImportedValueDiscrepancy,
    val resolution: InformationRequestImportedValueDiscrepancyResolution?,
)

data class InformationRequestImportedValueView(
    val value: InformationRequestImportedValue,
    val decision: InformationRequestImportedValueDecision?,
    val discrepancies: List<InformationRequestDiscrepancyView>,
)
