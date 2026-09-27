package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordRetentionSchedule
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class RecordPreservationAudit @Inject constructor(
    private val auditRecorder: AuditRecorder,
)
{
    fun hold(eventType: AuditEventType, hold: RecordPreservationHold, principal: PrincipalRef)
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = scopeOf(RecordOwnerRef(hold.ownerKind, hold.ownerId)),
                eventTypeKey = eventType.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = principal.id,
                actorKind = AuditActorKind.forPrincipal(principal.kind),
                actorRole = "RECORD_PRESERVATION",
                targetType = "AUDIT_LEGAL_HOLD",
                targetId = hold.id.toString(),
                payload = buildMap {
                    put("resource_type", hold.resourceType)
                    put("resource_id", hold.resourceId)
                    put("status", hold.status.name)
                    put("scope", hold.scope.name)
                    put("holdRevision", hold.holdRevision.toString())
                    hold.caseReference?.let { put("case_reference", it) }
                },
                idempotencyKey = "${eventType.key}|${hold.id}|${hold.holdRevision}",
            ),
        )
    }

    fun schedule(schedule: RecordRetentionSchedule, principal: PrincipalRef)
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = scopeOf(RecordOwnerRef(schedule.ownerKind, schedule.ownerId)),
                eventTypeKey = AuditEventType.RECORD_RETENTION_SCHEDULE_UPDATED.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = principal.id,
                actorKind = AuditActorKind.forPrincipal(principal.kind),
                actorRole = "RECORD_PRESERVATION",
                targetType = "RECORD_RETENTION_SCHEDULE",
                targetId = schedule.id.toString(),
                payload = buildMap {
                    put("resource_type", schedule.resourceType)
                    put("versionNumber", schedule.versionNumber.toString())
                    put("minimumRetentionDays", schedule.minimumRetentionDays.toString())
                    schedule.disposalAfterDays?.let { put("disposalAfterDays", it.toString()) }
                },
                idempotencyKey = "${AuditEventType.RECORD_RETENTION_SCHEDULE_UPDATED.key}|${schedule.id}",
            ),
        )
    }

    @Suppress("LongParameterList")
    fun disposal(
        eventType: AuditEventType,
        owner: RecordOwnerRef,
        resourceType: String,
        resourceId: String,
        principal: PrincipalRef,
        payload: Map<String, String>,
        idempotencyKey: String,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
    )
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = scopeOf(owner),
                eventTypeKey = eventType.key,
                outcome = outcome,
                actorId = principal.id,
                actorKind = AuditActorKind.forPrincipal(principal.kind),
                actorRole = "RECORD_PRESERVATION",
                targetType = resourceType,
                targetId = resourceId,
                payload = payload,
                idempotencyKey = idempotencyKey,
            ),
        )
    }

    private fun scopeOf(owner: RecordOwnerRef): AuditOwnerScope = when (owner.kind)
    {
        RecordOwnerKind.PLATFORM -> AuditOwnerScope.Platform
        RecordOwnerKind.ORGANIZATION -> AuditOwnerScope.Organization(requireNotNull(owner.id))
        RecordOwnerKind.USER -> AuditOwnerScope.Personal(requireNotNull(owner.id))
    }
}
