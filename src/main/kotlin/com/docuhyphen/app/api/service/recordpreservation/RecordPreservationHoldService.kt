package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationErrorCatalog
import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.exception.RecordPreservationNotFoundException
import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldEvent
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldEventKind
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldStatus
import com.docuhyphen.app.api.model.recordpreservation.*
import com.docuhyphen.app.api.repository.recordpreservation.RecordDisposalClaimRepository
import com.docuhyphen.app.api.repository.recordpreservation.RecordPreservationHoldEventRepository
import com.docuhyphen.app.api.repository.recordpreservation.RecordPreservationHoldRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*

@ApplicationScoped
class RecordPreservationHoldService @Inject constructor(
    private val holdRepository: RecordPreservationHoldRepository,
    private val eventRepository: RecordPreservationHoldEventRepository,
    private val claimRepository: RecordDisposalClaimRepository,
    private val audit: RecordPreservationAudit,
    private val clock: Clock,
)
{
    @Transactional
    fun place(command: PlaceRecordPreservationHoldCommand): RecordPreservationHoldView
    {
        val reason = requireText(command.reason, "A hold states its reason")
        val now = clock.instant()
        val hold = RecordPreservationHold().apply {
            ownerKind = command.owner.kind
            ownerId = command.owner.id
            resourceType = normalizedType(command.resourceType)
            resourceId = normalizedId(command.resourceId)
            this.reason = reason
            caseReference = command.caseReference?.trim()?.ifBlank { null }
            scope = command.scope
            effectiveFrom = Timestamp.from(command.effectiveFrom ?: now)
            placedByPrincipalKind = command.principal.kind
            placedByPrincipalId = command.principal.id
            placedAt = Timestamp.from(now)
            createdAt = Timestamp.from(now)
            updatedAt = Timestamp.from(now)
        }
        refuseWhileUnderDisposal(hold)
        holdRepository.save(hold)
        append(hold, RecordPreservationHoldEventKind.PLACED, reason, command.principal, now)
        audit.hold(AuditEventType.AUDIT_LEGAL_HOLD_PLACED, hold, command.principal)
        return view(hold)
    }

    @Transactional
    fun changeScope(command: ChangeRecordPreservationHoldScopeCommand): RecordPreservationHoldView
    {
        val reason = requireText(command.reason, "A scope change states its reason")
        val hold = activeHold(command.holdId, command.owner)
        if (hold.scope == command.scope)
        {
            throw RecordPreservationException(
                RecordPreservationErrorCatalog.HOLD_SCOPE_UNCHANGED,
                "The hold already has this scope"
            )
        }
        val now = clock.instant()
        hold.scope = command.scope
        hold.holdRevision += 1
        hold.updatedAt = Timestamp.from(now)
        refuseWhileUnderDisposal(hold)
        holdRepository.update(hold)
        append(hold, RecordPreservationHoldEventKind.SCOPE_CHANGED, reason, command.principal, now)
        audit.hold(AuditEventType.AUDIT_LEGAL_HOLD_SCOPE_CHANGED, hold, command.principal)
        return view(hold)
    }

    @Transactional
    fun release(command: ReleaseRecordPreservationHoldCommand): RecordPreservationHoldView
    {
        val reason = requireText(command.reason, "A release states its reason")
        val hold = activeHold(command.holdId, command.owner)
        val now = clock.instant()
        hold.status = RecordPreservationHoldStatus.RELEASED
        hold.releasedByPrincipalKind = command.principal.kind
        hold.releasedByPrincipalId = command.principal.id
        hold.releasedAt = Timestamp.from(now)
        hold.releaseReason = reason
        hold.holdRevision += 1
        hold.updatedAt = Timestamp.from(now)
        holdRepository.update(hold)
        append(hold, RecordPreservationHoldEventKind.RELEASED, reason, command.principal, now)
        audit.hold(AuditEventType.AUDIT_LEGAL_HOLD_RELEASED, hold, command.principal)
        return view(hold)
    }

    fun holds(owner: RecordOwnerRef, statuses: Set<RecordPreservationHoldStatus>): List<RecordPreservationHold> =
        holdRepository.findForOwner(owner, statuses)

    fun hold(holdId: UUID, owner: RecordOwnerRef): RecordPreservationHoldView =
        view(ownedHold(holdRepository.findById(holdId), owner))

    fun coveringHolds(owner: RecordOwnerRef, keys: Collection<RecordPreservationKey>): List<RecordPreservationHold> =
        holdRepository.findActiveCovering(owner, keys)

    fun isPreserved(owner: RecordOwnerRef, keys: Collection<RecordPreservationKey>): Boolean =
        coveringHolds(owner, keys).isNotEmpty()

    private fun activeHold(holdId: UUID, owner: RecordOwnerRef): RecordPreservationHold
    {
        val hold = ownedHold(holdRepository.findForUpdate(holdId), owner)
        if (hold.status != RecordPreservationHoldStatus.ACTIVE)
        {
            throw RecordPreservationException(
                RecordPreservationErrorCatalog.HOLD_RELEASED,
                "A released hold does not change"
            )
        }
        return hold
    }

    private fun ownedHold(hold: RecordPreservationHold?, owner: RecordOwnerRef): RecordPreservationHold =
        hold?.takeIf { it.ownerKind == owner.kind && it.ownerId == owner.id }
            ?: throw RecordPreservationNotFoundException("Record preservation hold not found")

    private fun refuseWhileUnderDisposal(hold: RecordPreservationHold)
    {
        if (claimRepository.hasOpenClaimCovering(hold))
        {
            throw RecordPreservationException(
                RecordPreservationErrorCatalog.DISPOSAL_IN_PROGRESS,
                "A record under disposal cannot be placed on hold",
            )
        }
    }

    private fun append(
        hold: RecordPreservationHold,
        kind: RecordPreservationHoldEventKind,
        reason: String,
        principal: PrincipalRef,
        at: Instant,
    )
    {
        eventRepository.save(
            RecordPreservationHoldEvent().apply {
                holdId = hold.id
                eventNumber = eventRepository.nextEventNumber(hold.id)
                eventKind = kind
                scope = hold.scope
                this.reason = reason
                principalKind = principal.kind
                principalId = principal.id
                occurredAt = Timestamp.from(at)
            },
        )
    }

    private fun view(hold: RecordPreservationHold) =
        RecordPreservationHoldView(hold, eventRepository.findForHold(hold.id))

    private fun requireText(raw: String, message: String): String =
        raw.trim().takeIf { it.isNotEmpty() } ?: throw RecordPreservationRequestException(message)

    private fun normalizedType(raw: String): String =
        requireText(raw, "A hold names its resource type").uppercase()

    private fun normalizedId(raw: String): String
    {
        val trimmed = requireText(raw, "A hold names its resource")
        return runCatching { UUID.fromString(trimmed).toString() }.getOrDefault(trimmed)
    }
}
