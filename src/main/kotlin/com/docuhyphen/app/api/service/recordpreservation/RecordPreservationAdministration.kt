package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldStatus
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.recordpreservation.ChangeRecordPreservationHoldScopeCommand
import com.docuhyphen.app.api.model.recordpreservation.PlaceRecordPreservationHoldCommand
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalView
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationHoldView
import com.docuhyphen.app.api.model.recordpreservation.RecordRetentionScheduleView
import com.docuhyphen.app.api.model.recordpreservation.ReleaseRecordPreservationHoldCommand
import com.docuhyphen.app.api.service.auth.authz.Action
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class RecordPreservationAdministration @Inject constructor(
    private val access: RecordOwnerScopeAccess,
    private val holds: RecordPreservationHoldService,
    private val schedules: RecordRetentionScheduleService,
    private val disposals: RecordDisposalService,
    private val entitlements: RecordPreservationEntitlementGuard,
)
{
    @Suppress("LongParameterList")
    fun placeHold(
        resourceType: String,
        resourceId: String,
        scope: RecordPreservationScope,
        reason: String,
        caseReference: String?,
        effectiveFrom: Instant?,
    ): RecordPreservationHoldView
    {
        val owner = access.currentOwner()
        val principal = access.requireAccess(owner, Action.AUDIT_LEGAL_HOLD_MANAGE)
        entitlements.requireMutation(owner)
        return holds.place(PlaceRecordPreservationHoldCommand(owner, resourceType, resourceId, scope, reason, caseReference, effectiveFrom, principal))
    }

    fun changeHoldScope(holdId: UUID, scope: RecordPreservationScope, reason: String): RecordPreservationHoldView
    {
        val owner = access.currentOwner()
        val principal = access.requireAccess(owner, Action.AUDIT_LEGAL_HOLD_MANAGE)
        entitlements.requireMutation(owner)
        return holds.changeScope(ChangeRecordPreservationHoldScopeCommand(holdId, owner, scope, reason, principal))
    }

    fun releaseHold(holdId: UUID, reason: String): RecordPreservationHoldView
    {
        val owner = access.currentOwner()
        val principal = access.requireAccess(owner, Action.AUDIT_LEGAL_HOLD_MANAGE)
        entitlements.requireMutation(owner)
        return holds.release(ReleaseRecordPreservationHoldCommand(holdId, owner, reason, principal))
    }

    fun holds(statuses: Set<RecordPreservationHoldStatus>): List<RecordPreservationHold>
    {
        val owner = access.currentOwner()
        access.requireAccess(owner, Action.ORG_READ_AUDIT)
        return holds.holds(owner, statuses.ifEmpty { RecordPreservationHoldStatus.entries.toSet() })
    }

    fun hold(holdId: UUID): RecordPreservationHoldView
    {
        val owner = access.currentOwner()
        access.requireAccess(owner, Action.ORG_READ_AUDIT)
        return holds.hold(holdId, owner)
    }

    fun schedule(resourceType: String): RecordRetentionScheduleView
    {
        val owner = access.currentOwner()
        access.requireAccess(owner, Action.ORG_READ_AUDIT)
        return schedules.schedule(owner, resourceType)
    }

    fun publishSchedule(resourceType: String, minimumRetentionDays: Int, disposalAfterDays: Int?): RecordRetentionScheduleView
    {
        val owner = access.currentOwner()
        val principal = access.requireAccess(owner, Action.AUDIT_RETENTION_MANAGE)
        entitlements.requireMutation(owner)
        return schedules.publish(PublishRecordRetentionScheduleCommand(owner, resourceType, minimumRetentionDays, disposalAfterDays, principal))
    }

    fun disposals(): List<RecordDisposalView>
    {
        val owner = access.currentOwner()
        access.requireAccess(owner, Action.ORG_READ_AUDIT)
        return disposals.viewsFor(owner)
    }
}
