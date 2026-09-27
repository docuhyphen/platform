package com.docuhyphen.app.api.model.recordpreservation

import com.docuhyphen.app.api.model.entity.RecordDisposalBasis
import com.docuhyphen.app.api.model.entity.RecordDisposalClaim
import com.docuhyphen.app.api.model.entity.RecordDisposalObject
import com.docuhyphen.app.api.model.entity.RecordDisposalTombstone
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldEvent
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.entity.RecordRetentionSchedule
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import java.time.Instant
import java.util.UUID

data class RecordOwnerRef(
    val kind: RecordOwnerKind,
    val id: UUID?,
)
{
    init
    {
        require((kind == RecordOwnerKind.PLATFORM) == (id == null)) { "Only the platform owner has no identifier" }
    }

    companion object
    {
        val PLATFORM = RecordOwnerRef(RecordOwnerKind.PLATFORM, null)

        fun organization(id: UUID) = RecordOwnerRef(RecordOwnerKind.ORGANIZATION, id)

        fun user(id: UUID) = RecordOwnerRef(RecordOwnerKind.USER, id)
    }
}

data class RecordPreservationKey(
    val resourceType: String,
    val resourceId: String,
    val direct: Boolean,
)

data class PlaceRecordPreservationHoldCommand(
    val owner: RecordOwnerRef,
    val resourceType: String,
    val resourceId: String,
    val scope: RecordPreservationScope,
    val reason: String,
    val caseReference: String?,
    val effectiveFrom: Instant?,
    val principal: PrincipalRef,
)

data class ChangeRecordPreservationHoldScopeCommand(
    val holdId: UUID,
    val owner: RecordOwnerRef,
    val scope: RecordPreservationScope,
    val reason: String,
    val principal: PrincipalRef,
)

data class ReleaseRecordPreservationHoldCommand(
    val holdId: UUID,
    val owner: RecordOwnerRef,
    val reason: String,
    val principal: PrincipalRef,
)

data class RecordPreservationHoldView(
    val hold: RecordPreservationHold,
    val events: List<RecordPreservationHoldEvent>,
)

data class PublishRecordRetentionScheduleCommand(
    val owner: RecordOwnerRef,
    val resourceType: String,
    val minimumRetentionDays: Int,
    val disposalAfterDays: Int?,
    val principal: PrincipalRef,
)

data class RecordRetentionScheduleView(
    val current: RecordRetentionSchedule?,
    val versions: List<RecordRetentionSchedule>,
)

data class RecordDisposalObjectCandidate(
    val documentId: UUID,
    val documentVersionId: UUID,
    val storageProvider: String?,
    val storageLocatorKind: String?,
    val storageLocator: String?,
    val retainedReason: String?,
)

data class OpenRecordDisposalClaimCommand(
    val resourceType: String,
    val resourceId: UUID,
    val owner: RecordOwnerRef,
    val basis: RecordDisposalBasis,
    val retentionScheduleId: UUID?,
    val privacyRequestId: UUID?,
    val principal: PrincipalRef,
    val scopeKeys: List<RecordPreservationKey>,
    val objects: List<RecordDisposalObjectCandidate>,
)

data class RecordDisposalView(
    val claim: RecordDisposalClaim,
    val objects: List<RecordDisposalObject>,
    val tombstone: RecordDisposalTombstone?,
)

object RecordPreservationResourceTypes
{
    const val INFORMATION_REQUEST = "INFORMATION_REQUEST"
    const val EXCHANGE = "EXCHANGE"
    const val ORGANIZATION = "ORGANIZATION"
    const val APP_USER = "APP_USER"
    const val SUBJECT_IDENTITY = "SUBJECT_IDENTITY"
    const val DOCUMENT = "DOCUMENT"
    const val DOCUMENT_VERSION = "DOCUMENT_VERSION"
}

enum class RecordTransferVerdict
{
    PERMITTED,
    REFUSED,
}

enum class RecordOwnershipChangeDecision
{
    RETAIN,
    REVOKE,
}

data class RecordOwnershipChange(
    val owner: RecordOwnerRef,
    val appUserId: UUID,
    val resourceType: String,
    val resourceId: UUID,
    val parentResourceId: UUID,
    val roleKey: String,
)

data class RecordOwnershipChangeOutcome(
    val resourceId: UUID,
    val parentResourceId: UUID,
    val decision: RecordOwnershipChangeDecision,
)
