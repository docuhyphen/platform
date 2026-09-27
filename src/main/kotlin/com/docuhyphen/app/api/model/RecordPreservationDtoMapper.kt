package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestDisposalStandingDto
import com.docuhyphen.app.api.model.dto.RecordDisposalDto
import com.docuhyphen.app.api.model.dto.RecordDisposalObjectDto
import com.docuhyphen.app.api.model.dto.RecordDisposalTombstoneDto
import com.docuhyphen.app.api.model.dto.RecordPreservationHoldDto
import com.docuhyphen.app.api.model.dto.RecordPreservationHoldEventDto
import com.docuhyphen.app.api.model.dto.RecordRetentionScheduleDto
import com.docuhyphen.app.api.model.dto.RecordRetentionScheduleVersionDto
import com.docuhyphen.app.api.model.entity.RecordDisposalTombstone
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldEvent
import com.docuhyphen.app.api.model.entity.RecordRetentionSchedule
import com.docuhyphen.app.api.model.informationrequest.InformationRequestDisposalAssessment
import com.docuhyphen.app.api.model.informationrequest.InformationRequestDisposalStanding
import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalView
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationHoldView
import com.docuhyphen.app.api.model.recordpreservation.RecordRetentionScheduleView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.sql.Timestamp

object RecordPreservationDtoMapper
{
    fun toDto(view: RecordPreservationHoldView): RecordPreservationHoldDto = toDto(view.hold, view.events)

    fun toDto(hold: RecordPreservationHold, events: List<RecordPreservationHoldEvent> = emptyList()) = RecordPreservationHoldDto(
        id = hold.id,
        ownerKind = hold.ownerKind,
        ownerId = hold.ownerId,
        resourceType = hold.resourceType,
        resourceId = hold.resourceId,
        scope = hold.scope,
        status = hold.status,
        reason = hold.reason,
        caseReference = hold.caseReference,
        effectiveFrom = hold.effectiveFrom,
        placedByPrincipalKind = hold.placedByPrincipalKind,
        placedByPrincipalId = hold.placedByPrincipalId,
        placedAt = hold.placedAt,
        releasedByPrincipalKind = hold.releasedByPrincipalKind,
        releasedByPrincipalId = hold.releasedByPrincipalId,
        releasedAt = hold.releasedAt,
        releaseReason = hold.releaseReason,
        holdRevision = hold.holdRevision,
        events = events.map {
            RecordPreservationHoldEventDto(it.eventNumber, it.eventKind, it.scope, it.reason, it.principalKind, it.principalId, it.occurredAt)
        },
    )

    fun toDto(resourceType: String, view: RecordRetentionScheduleView) = RecordRetentionScheduleDto(
        resourceType = resourceType.trim().uppercase(),
        current = view.current?.let(::toDto),
        versions = view.versions.map(::toDto),
    )

    fun toDto(schedule: RecordRetentionSchedule) = RecordRetentionScheduleVersionDto(
        id = schedule.id,
        versionNumber = schedule.versionNumber,
        minimumRetentionDays = schedule.minimumRetentionDays,
        disposalAfterDays = schedule.disposalAfterDays,
        recordedByPrincipalKind = schedule.recordedByPrincipalKind,
        recordedByPrincipalId = schedule.recordedByPrincipalId,
        recordedAt = schedule.recordedAt,
    )

    fun toDto(view: RecordDisposalView) = RecordDisposalDto(
        claimId = view.claim.id,
        resourceType = view.claim.resourceType,
        resourceId = view.claim.resourceId,
        basis = view.claim.basis,
        state = view.claim.state,
        claimedAt = view.claim.claimedAt,
        objectsDeletedAt = view.claim.objectsDeletedAt,
        finalizedAt = view.claim.finalizedAt,
        attemptCount = view.claim.attemptCount,
        lastErrorCode = view.claim.lastErrorCode,
        objects = view.objects.map {
            RecordDisposalObjectDto(it.documentId, it.documentVersionId, it.retained, it.retainedReason, it.deletionOutcome, it.deletedAt)
        },
        tombstone = view.tombstone?.let(::toDto),
    )

    fun toDto(standing: InformationRequestDisposalStanding): InformationRequestDisposalStandingDto
    {
        val refused = standing.assessment as? InformationRequestDisposalAssessment.Refused
        val eligible = standing.assessment as? InformationRequestDisposalAssessment.Eligible
        return InformationRequestDisposalStandingDto(
            requestId = standing.requestId,
            eligible = eligible != null,
            reasonCode = refused?.reasonCode,
            detail = refused?.detail,
            eligibleFrom = refused?.eligibleFrom?.let(Timestamp::from),
            schedule = standing.assessment?.schedule?.let(::toDto),
            holds = standing.assessment?.holds.orEmpty().map { toDto(it) },
            retainedObjectCount = eligible?.objects?.count { it.retainedReason != null } ?: 0,
            disposal = standing.disposal?.let(::toDto),
        )
    }

    private fun toDto(tombstone: RecordDisposalTombstone) = RecordDisposalTombstoneDto(
        removedRowCounts = runCatching {
            Json.parseToJsonElement(tombstone.removedRowsJson).jsonObject.mapValues { it.value.jsonPrimitive.longOrNull ?: 0 }
        }.getOrDefault(emptyMap()),
        deletedObjectCount = tombstone.deletedObjectCount,
        retainedObjectCount = tombstone.retainedObjectCount,
        disposedAt = tombstone.disposedAt,
    )
}
