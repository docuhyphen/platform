package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

enum class InformationRequestConnectorKind
{
    STRUCTURED_EVIDENCE,
    EXTERNAL_VERIFICATION,
}

enum class InformationRequestConnectorExchangeState
{
    REQUESTED,
    PENDING,
    COMPLETED,
    FAILED,
}

enum class InformationRequestImportedValueSource
{
    MANUAL,
    CONNECTOR,
}

enum class InformationRequestSourceConfidence
{
    ASSERTED,
    MATCHED,
    VERIFIED,
}

enum class InformationRequestImportedValueDecisionKind
{
    ACCEPTED,
    REJECTED,
}

enum class InformationRequestDiscrepancyResolution
{
    RESPONSE_STANDS,
    FOLLOW_UP_REQUESTED,
}

@Entity
@Table(name = "information_request_connector_exchange")
class InformationRequestConnectorExchange
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "information_request_requirement_id", nullable = false)
    lateinit var informationRequestRequirementId: UUID

    @Column(name = "connector_key", nullable = false, length = 128)
    lateinit var connectorKey: String

    @Column(name = "connector_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var connectorKind: InformationRequestConnectorKind

    @Column(name = "contract_version", nullable = false)
    var contractVersion: Int = 1

    @Column(name = "state", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var state: InformationRequestConnectorExchangeState = InformationRequestConnectorExchangeState.REQUESTED

    @Column(name = "lookup_reference", length = 256)
    var lookupReference: String? = null

    @Column(name = "external_reference", length = 256)
    var externalReference: String? = null

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0

    @Column(name = "next_attempt_at")
    var nextAttemptAt: Timestamp? = null

    @Column(name = "failure_code", length = 128)
    var failureCode: String? = null

    @Column(name = "requested_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var requestedByPrincipalKind: PrincipalKind

    @Column(name = "requested_by_principal_id", nullable = false)
    lateinit var requestedByPrincipalId: UUID

    @Column(name = "requested_at", nullable = false)
    var requestedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "completed_at")
    var completedAt: Timestamp? = null
}

@Entity
@Table(name = "information_request_imported_value")
class InformationRequestImportedValue
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "information_request_requirement_id", nullable = false)
    lateinit var informationRequestRequirementId: UUID

    @Column(name = "source_kind", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    lateinit var sourceKind: InformationRequestImportedValueSource

    @Column(name = "connector_exchange_id")
    var connectorExchangeId: UUID? = null

    @Column(name = "source_reference", nullable = false, length = 256)
    lateinit var sourceReference: String

    @Column(name = "result_key", nullable = false, length = 128)
    lateinit var resultKey: String

    @Column(name = "value_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var valueType: FieldValueType

    @Column(name = "canonical_value", nullable = false)
    lateinit var canonicalValue: String

    @Column(name = "confidence", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    lateinit var confidence: InformationRequestSourceConfidence

    @Column(name = "verified_at")
    var verifiedAt: Timestamp? = null

    @Column(name = "expires_at")
    var expiresAt: Timestamp? = null

    @Column(name = "provenance_reference", nullable = false, length = 256)
    lateinit var provenanceReference: String

    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var recordedByPrincipalKind: PrincipalKind

    @Column(name = "recorded_by_principal_id", nullable = false)
    lateinit var recordedByPrincipalId: UUID

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "information_request_imported_value_decision")
class InformationRequestImportedValueDecision
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "imported_value_id", nullable = false)
    lateinit var importedValueId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "decision", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    lateinit var decision: InformationRequestImportedValueDecisionKind

    @Column(name = "reason_code", nullable = false, length = 128)
    lateinit var reasonCode: String

    @Column(name = "decided_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var decidedByPrincipalKind: PrincipalKind

    @Column(name = "decided_by_principal_id", nullable = false)
    lateinit var decidedByPrincipalId: UUID

    @Column(name = "decided_at", nullable = false)
    var decidedAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "information_request_imported_value_discrepancy")
class InformationRequestImportedValueDiscrepancy
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "imported_value_id", nullable = false)
    lateinit var importedValueId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "response_id", nullable = false)
    lateinit var responseId: UUID

    @Column(name = "response_revision", nullable = false)
    var responseRevision: Long = 0

    @Column(name = "imported_canonical_value", nullable = false)
    lateinit var importedCanonicalValue: String

    @Column(name = "response_canonical_value", nullable = false)
    lateinit var responseCanonicalValue: String

    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var recordedByPrincipalKind: PrincipalKind

    @Column(name = "recorded_by_principal_id", nullable = false)
    lateinit var recordedByPrincipalId: UUID

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "information_request_imported_value_discrepancy_resolution")
class InformationRequestImportedValueDiscrepancyResolution
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "discrepancy_id", nullable = false)
    lateinit var discrepancyId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "resolution", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var resolution: InformationRequestDiscrepancyResolution

    @Column(name = "reason_code", nullable = false, length = 128)
    lateinit var reasonCode: String

    @Column(name = "resolved_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var resolvedByPrincipalKind: PrincipalKind

    @Column(name = "resolved_by_principal_id", nullable = false)
    lateinit var resolvedByPrincipalId: UUID

    @Column(name = "resolved_at", nullable = false)
    var resolvedAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "information_request_generated_output")
class InformationRequestGeneratedOutput
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "package_id")
    var packageId: UUID? = null

    @Column(name = "output_key", nullable = false, length = 128)
    lateinit var outputKey: String

    @Column(name = "external_reference", nullable = false, length = 512)
    lateinit var externalReference: String

    @Column(name = "content_hash_sha256", length = 64)
    var contentHashSha256: String? = null

    @Column(name = "media_type", length = 128)
    var mediaType: String? = null

    @Column(name = "produced_by_source", nullable = false, length = 256)
    lateinit var producedBySource: String

    @Column(name = "produced_at", nullable = false)
    var producedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var recordedByPrincipalKind: PrincipalKind

    @Column(name = "recorded_by_principal_id", nullable = false)
    lateinit var recordedByPrincipalId: UUID

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())
}
