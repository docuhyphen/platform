package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldStatus
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.recordpreservation.*
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.canonicalForm
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.*

@ApplicationScoped
class RecordPreservationAdministration @Inject constructor(
    private val access: RecordOwnerScopeAccess,
    private val holds: RecordPreservationHoldService,
    private val schedules: RecordRetentionScheduleService,
    private val disposals: RecordDisposalService,
    private val entitlements: RecordPreservationEntitlementGuard,
    private val commandReceiptService: CommandReceiptService,
    private val targets: RecordPreservationTargets,
)
{
    @Suppress("LongParameterList")
    @Transactional
    fun placeHold(
        resourceType: String,
        resourceId: String,
        scope: RecordPreservationScope,
        reason: String,
        caseReference: String?,
        effectiveFrom: Instant?,
        idempotencyKey: String,
    ): RecordPreservationHoldView
    {
        val owner = access.currentOwner()
        val principal = access.requireAccess(owner, Action.AUDIT_LEGAL_HOLD_MANAGE)
        entitlements.requireMutation(owner)
        targets.requireHoldable(owner, resourceType, resourceId)
        holds.lockPlacements(owner)
        val receipt = receiptOf(
            RecordOwnerReceiptScope.of(ResourceType.RECORD_PRESERVATION_HOLD, owner),
            PLACE_OPERATION,
            principal,
            idempotencyKey,
            listOf(
                resourceType.trim().uppercase(),
                resourceId.trim(),
                scope.name,
                reason.trim(),
                caseReference?.trim().orEmpty(),
                effectiveFrom?.toString().orEmpty(),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val view = holds.place(
                    PlaceRecordPreservationHoldCommand(
                        owner,
                        resourceType,
                        resourceId,
                        scope,
                        reason,
                        caseReference,
                        effectiveFrom,
                        principal
                    )
                )
                CommandMutationResult(view, resultOf(view.hold))
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> holds.hold(decision.result.resourceId, owner)
        }
    }

    @Transactional
    fun changeHoldScope(
        holdId: UUID,
        scope: RecordPreservationScope,
        reason: String,
        precondition: CommandPrecondition,
        idempotencyKey: String,
    ): RecordPreservationHoldView
    {
        val owner = access.currentOwner()
        val principal = access.requireAccess(owner, Action.AUDIT_LEGAL_HOLD_MANAGE)
        entitlements.requireMutation(owner)
        val locked = holds.lockOwned(holdId, owner)
        val receipt = receiptOf(
            ResourceRef(ResourceType.RECORD_PRESERVATION_HOLD, holdId),
            CHANGE_SCOPE_OPERATION,
            principal,
            idempotencyKey,
            listOf(holdId.toString(), scope.name, reason.trim(), precondition.canonicalForm()),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                precondition.requireSatisfiedBy(RecordPreservationETag.holdOf(locked))
                val view =
                    holds.changeScope(ChangeRecordPreservationHoldScopeCommand(holdId, owner, scope, reason, principal))
                CommandMutationResult(view, resultOf(view.hold))
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> holds.hold(holdId, owner)
        }
    }

    @Transactional
    fun releaseHold(
        holdId: UUID,
        reason: String,
        precondition: CommandPrecondition,
        idempotencyKey: String,
    ): RecordPreservationHoldView
    {
        val owner = access.currentOwner()
        val principal = access.requireAccess(owner, Action.AUDIT_LEGAL_HOLD_MANAGE)
        entitlements.requireMutation(owner)
        val locked = holds.lockOwned(holdId, owner)
        val receipt = receiptOf(
            ResourceRef(ResourceType.RECORD_PRESERVATION_HOLD, holdId),
            RELEASE_OPERATION,
            principal,
            idempotencyKey,
            listOf(holdId.toString(), reason.trim(), precondition.canonicalForm()),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                precondition.requireSatisfiedBy(RecordPreservationETag.holdOf(locked))
                val view = holds.release(ReleaseRecordPreservationHoldCommand(holdId, owner, reason, principal))
                CommandMutationResult(view, resultOf(view.hold))
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> holds.hold(holdId, owner)
        }
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

    @Transactional
    fun publishSchedule(
        resourceType: String,
        minimumRetentionDays: Int,
        disposalAfterDays: Int?,
        precondition: CommandPrecondition,
        idempotencyKey: String,
    ): RecordRetentionScheduleView
    {
        val owner = access.currentOwner()
        val principal = access.requireAccess(owner, Action.AUDIT_RETENTION_MANAGE)
        entitlements.requireMutation(owner)
        schedules.lockSeries(owner, resourceType)
        val receipt = receiptOf(
            RecordOwnerReceiptScope.of(ResourceType.RECORD_RETENTION_SCHEDULE, owner),
            PUBLISH_OPERATION,
            principal,
            idempotencyKey,
            listOf(
                resourceType.trim().uppercase(),
                minimumRetentionDays.toString(),
                disposalAfterDays?.toString().orEmpty(),
                precondition.canonicalForm(),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                precondition.requireSatisfiedBy(schedules.schedule(owner, resourceType).etag)
                val view = schedules.publish(
                    PublishRecordRetentionScheduleCommand(
                        owner,
                        resourceType,
                        minimumRetentionDays,
                        disposalAfterDays,
                        principal
                    )
                )
                val published = requireNotNull(view.current)
                CommandMutationResult(
                    view,
                    CommandResultReference(
                        ResourceType.RECORD_RETENTION_SCHEDULE,
                        published.id,
                        published.versionNumber.toLong(),
                        view.etag,
                    ),
                )
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> schedules.schedule(owner, resourceType)
        }
    }

    fun disposals(): List<RecordDisposalView>
    {
        val owner = access.currentOwner()
        access.requireAccess(owner, Action.ORG_READ_AUDIT)
        return disposals.viewsFor(owner)
    }

    private fun receiptOf(
        resource: ResourceRef,
        operation: String,
        principal: PrincipalRef,
        idempotencyKey: String,
        request: List<String>,
    ): CommandReceiptRequest {
        val key = idempotencyKey.trim()
        if (key.isEmpty()) throw RecordPreservationRequestException("Idempotency-Key is required")
        return CommandReceiptRequest(
            resource = resource,
            operation = operation,
            actor = CommandActorRef.principal(principal),
            idempotencyKey = key,
            requestFingerprint = CommandRequestFingerprint.of(operation, *request.toTypedArray()),
        )
    }

    private fun resultOf(hold: RecordPreservationHold) =
        CommandResultReference(
            ResourceType.RECORD_PRESERVATION_HOLD,
            hold.id,
            hold.holdRevision,
            RecordPreservationETag.holdOf(hold),
        )

    private companion object {
        const val PLACE_OPERATION = "record_preservation.hold.place"
        const val CHANGE_SCOPE_OPERATION = "record_preservation.hold.change_scope"
        const val RELEASE_OPERATION = "record_preservation.hold.release"
        const val PUBLISH_OPERATION = "record_preservation.retention_schedule.publish"
    }
}
