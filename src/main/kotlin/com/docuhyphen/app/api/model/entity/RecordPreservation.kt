package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class RecordOwnerKind
{
    PLATFORM,
    ORGANIZATION,
    USER,
}

enum class RecordPreservationScope
{
    RESOURCE,
    DESCENDANTS_AND_REFERENCES,
}

enum class RecordPreservationHoldStatus
{
    ACTIVE,
    RELEASED,
}

enum class RecordPreservationHoldEventKind
{
    PLACED,
    SCOPE_CHANGED,
    RELEASED,
}

enum class RecordDisposalBasis
{
    RETENTION_SCHEDULE,
    PRIVACY_DELETION,
}

enum class RecordDisposalState
{
    CLAIMED,
    OBJECTS_DELETED,
    FINALIZED,
}

enum class RecordDisposalDeletionOutcome
{
    DELETED,
    ABSENT,
}

@Entity
@Table(name = "audit_legal_hold")
class RecordPreservationHold
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_kind", nullable = false, length = 32)
    var ownerKind: RecordOwnerKind = RecordOwnerKind.ORGANIZATION

    @Column(name = "owner_id")
    var ownerId: UUID? = null

    @Column(name = "resource_type", nullable = false, length = 64)
    lateinit var resourceType: String

    @Column(name = "resource_id", nullable = false, length = 128)
    lateinit var resourceId: String

    @Column(name = "reason", nullable = false, length = 2048)
    var reason: String = ""

    @Column(name = "case_reference", length = 256)
    var caseReference: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: RecordPreservationHoldStatus = RecordPreservationHoldStatus.ACTIVE

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    var scope: RecordPreservationScope = RecordPreservationScope.RESOURCE

    @Column(name = "effective_from", nullable = false)
    var effectiveFrom: Timestamp = Timestamp.from(Instant.now())

    @Enumerated(EnumType.STRING)
    @Column(name = "placed_by_principal_kind", nullable = false, length = 32)
    var placedByPrincipalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "placed_by_principal_id", nullable = false)
    lateinit var placedByPrincipalId: UUID

    @Column(name = "placed_at", nullable = false)
    var placedAt: Timestamp = Timestamp.from(Instant.now())

    @Enumerated(EnumType.STRING)
    @Column(name = "released_by_principal_kind", length = 32)
    var releasedByPrincipalKind: PrincipalKind? = null

    @Column(name = "released_by_principal_id")
    var releasedByPrincipalId: UUID? = null

    @Column(name = "released_at")
    var releasedAt: Timestamp? = null

    @Column(name = "release_reason", length = 2048)
    var releaseReason: String? = null

    @Column(name = "hold_revision", nullable = false)
    var holdRevision: Long = 1

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "audit_legal_hold_event")
class RecordPreservationHoldEvent
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "hold_id", nullable = false)
    lateinit var holdId: UUID

    @Column(name = "event_number", nullable = false)
    var eventNumber: Int = 1

    @Enumerated(EnumType.STRING)
    @Column(name = "event_kind", nullable = false, length = 32)
    var eventKind: RecordPreservationHoldEventKind = RecordPreservationHoldEventKind.PLACED

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    var scope: RecordPreservationScope = RecordPreservationScope.RESOURCE

    @Column(name = "reason", nullable = false, length = 2048)
    var reason: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_kind", nullable = false, length = 32)
    var principalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "principal_id", nullable = false)
    lateinit var principalId: UUID

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "record_retention_schedule")
class RecordRetentionSchedule
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_kind", nullable = false, length = 32)
    var ownerKind: RecordOwnerKind = RecordOwnerKind.ORGANIZATION

    @Column(name = "owner_id", nullable = false)
    lateinit var ownerId: UUID

    @Column(name = "resource_type", nullable = false, length = 64)
    lateinit var resourceType: String

    @Column(name = "version_number", nullable = false)
    var versionNumber: Int = 1

    @Column(name = "minimum_retention_days", nullable = false)
    var minimumRetentionDays: Int = 0

    @Column(name = "disposal_after_days")
    var disposalAfterDays: Int? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    var recordedByPrincipalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "recorded_by_principal_id", nullable = false)
    lateinit var recordedByPrincipalId: UUID

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "record_disposal_claim")
class RecordDisposalClaim
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "resource_type", nullable = false, length = 64)
    lateinit var resourceType: String

    @Column(name = "resource_id", nullable = false)
    lateinit var resourceId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_kind", nullable = false, length = 32)
    var ownerKind: RecordOwnerKind = RecordOwnerKind.ORGANIZATION

    @Column(name = "owner_id", nullable = false)
    lateinit var ownerId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, length = 32)
    var basis: RecordDisposalBasis = RecordDisposalBasis.RETENTION_SCHEDULE

    @Column(name = "retention_schedule_id")
    var retentionScheduleId: UUID? = null

    @Column(name = "privacy_request_id")
    var privacyRequestId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    var state: RecordDisposalState = RecordDisposalState.CLAIMED

    @Enumerated(EnumType.STRING)
    @Column(name = "claimed_by_principal_kind", nullable = false, length = 32)
    var claimedByPrincipalKind: PrincipalKind = PrincipalKind.SERVICE_ACCOUNT

    @Column(name = "claimed_by_principal_id", nullable = false)
    lateinit var claimedByPrincipalId: UUID

    @Column(name = "claimed_at", nullable = false)
    var claimedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "objects_deleted_at")
    var objectsDeletedAt: Timestamp? = null

    @Column(name = "finalized_at")
    var finalizedAt: Timestamp? = null

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0

    @Column(name = "last_error_code", length = 128)
    var lastErrorCode: String? = null

    @Column(name = "claim_revision", nullable = false)
    var claimRevision: Long = 1
}

@Entity
@Table(name = "record_disposal_object")
class RecordDisposalObject
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "claim_id", nullable = false)
    lateinit var claimId: UUID

    @Column(name = "object_kind", nullable = false, length = 32)
    var objectKind: String = DOCUMENT_VERSION

    @Column(name = "document_id", nullable = false)
    lateinit var documentId: UUID

    @Column(name = "document_version_id", nullable = false)
    lateinit var documentVersionId: UUID

    @Column(name = "storage_provider", length = 32)
    var storageProvider: String? = null

    @Column(name = "storage_locator_kind", length = 32)
    var storageLocatorKind: String? = null

    @Column(name = "storage_locator", length = 1024)
    var storageLocator: String? = null

    @Column(name = "retained", nullable = false)
    var retained: Boolean = false

    @Column(name = "retained_reason", length = 64)
    var retainedReason: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "deletion_outcome", length = 16)
    var deletionOutcome: RecordDisposalDeletionOutcome? = null

    @Column(name = "deleted_at")
    var deletedAt: Timestamp? = null

    companion object
    {
        const val DOCUMENT_VERSION = "DOCUMENT_VERSION"
    }
}

@Entity
@Table(name = "record_disposal_tombstone")
class RecordDisposalTombstone
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "claim_id", nullable = false)
    lateinit var claimId: UUID

    @Column(name = "resource_type", nullable = false, length = 64)
    lateinit var resourceType: String

    @Column(name = "resource_id", nullable = false)
    lateinit var resourceId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_kind", nullable = false, length = 32)
    var ownerKind: RecordOwnerKind = RecordOwnerKind.ORGANIZATION

    @Column(name = "owner_id", nullable = false)
    lateinit var ownerId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, length = 32)
    var basis: RecordDisposalBasis = RecordDisposalBasis.RETENTION_SCHEDULE

    @Column(name = "removed_rows_json", nullable = false, columnDefinition = "text")
    var removedRowsJson: String = "{}"

    @Column(name = "deleted_object_count", nullable = false)
    var deletedObjectCount: Int = 0

    @Column(name = "retained_object_count", nullable = false)
    var retainedObjectCount: Int = 0

    @Column(name = "disposed_at", nullable = false)
    var disposedAt: Timestamp = Timestamp.from(Instant.now())
}
