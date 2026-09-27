package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RecordDisposalBasis
import com.docuhyphen.app.api.model.entity.RecordDisposalDeletionOutcome
import com.docuhyphen.app.api.model.entity.RecordDisposalState
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldEventKind
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldStatus
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class RecordPreservationHoldEventDto(
    val eventNumber: Int,
    val eventKind: RecordPreservationHoldEventKind,
    val scope: RecordPreservationScope,
    val reason: String,
    val principalKind: PrincipalKind,
    @Serializable(with = UUIDSerializer::class) val principalId: UUID,
    @Serializable(with = TimestampSerializer::class) val occurredAt: Timestamp,
)

@Serializable
data class RecordPreservationHoldDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val ownerKind: RecordOwnerKind,
    @Serializable(with = UUIDSerializer::class) val ownerId: UUID? = null,
    val resourceType: String,
    val resourceId: String,
    val scope: RecordPreservationScope,
    val status: RecordPreservationHoldStatus,
    val reason: String,
    val caseReference: String? = null,
    @Serializable(with = TimestampSerializer::class) val effectiveFrom: Timestamp,
    val placedByPrincipalKind: PrincipalKind,
    @Serializable(with = UUIDSerializer::class) val placedByPrincipalId: UUID,
    @Serializable(with = TimestampSerializer::class) val placedAt: Timestamp,
    val releasedByPrincipalKind: PrincipalKind? = null,
    @Serializable(with = UUIDSerializer::class) val releasedByPrincipalId: UUID? = null,
    @Serializable(with = TimestampSerializer::class) val releasedAt: Timestamp? = null,
    val releaseReason: String? = null,
    val holdRevision: Long,
    val events: List<RecordPreservationHoldEventDto> = emptyList(),
)

@Serializable
data class RecordRetentionScheduleVersionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val versionNumber: Int,
    val minimumRetentionDays: Int,
    val disposalAfterDays: Int? = null,
    val recordedByPrincipalKind: PrincipalKind,
    @Serializable(with = UUIDSerializer::class) val recordedByPrincipalId: UUID,
    @Serializable(with = TimestampSerializer::class) val recordedAt: Timestamp,
)

@Serializable
data class RecordRetentionScheduleDto(
    val resourceType: String,
    val current: RecordRetentionScheduleVersionDto? = null,
    val versions: List<RecordRetentionScheduleVersionDto>,
)

@Serializable
data class RecordDisposalObjectDto(
    @Serializable(with = UUIDSerializer::class) val documentId: UUID,
    @Serializable(with = UUIDSerializer::class) val documentVersionId: UUID,
    val retained: Boolean,
    val retainedReason: String? = null,
    val deletionOutcome: RecordDisposalDeletionOutcome? = null,
    @Serializable(with = TimestampSerializer::class) val deletedAt: Timestamp? = null,
)

@Serializable
data class RecordDisposalTombstoneDto(
    val removedRowCounts: Map<String, Long>,
    val deletedObjectCount: Int,
    val retainedObjectCount: Int,
    @Serializable(with = TimestampSerializer::class) val disposedAt: Timestamp,
)

@Serializable
data class RecordDisposalDto(
    @Serializable(with = UUIDSerializer::class) val claimId: UUID,
    val resourceType: String,
    @Serializable(with = UUIDSerializer::class) val resourceId: UUID,
    val basis: RecordDisposalBasis,
    val state: RecordDisposalState,
    @Serializable(with = TimestampSerializer::class) val claimedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val objectsDeletedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val finalizedAt: Timestamp? = null,
    val attemptCount: Int,
    val lastErrorCode: String? = null,
    val objects: List<RecordDisposalObjectDto>,
    val tombstone: RecordDisposalTombstoneDto? = null,
)

@Serializable
data class InformationRequestDisposalStandingDto(
    @Serializable(with = UUIDSerializer::class) val requestId: UUID,
    val eligible: Boolean,
    val reasonCode: String? = null,
    val detail: String? = null,
    @Serializable(with = TimestampSerializer::class) val eligibleFrom: Timestamp? = null,
    val schedule: RecordRetentionScheduleVersionDto? = null,
    val holds: List<RecordPreservationHoldDto> = emptyList(),
    val retainedObjectCount: Int = 0,
    val disposal: RecordDisposalDto? = null,
)
