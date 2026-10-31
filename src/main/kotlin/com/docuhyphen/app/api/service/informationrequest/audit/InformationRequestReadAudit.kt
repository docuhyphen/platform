package com.docuhyphen.app.api.service.informationrequest.audit

import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestAuditedRead
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestReadAudit @Inject constructor(
    private val auditRecorder: AuditRecorder,
    private val actorLabels: InformationRequestAuditActorLabelResolver,
) {
    fun record(read: InformationRequestAuditedRead) {
        val attribution = actorLabels.attribute(read.actor)
        auditRecorder.record(
            AuditEventDraft(
                owner = read.owner,
                eventTypeKey = read.eventType.key,
                outcome = read.outcome,
                actorId = attribution.actorId,
                actorKind = attribution.actorKind,
                actorLabel = attribution.actorLabel,
                sessionId = attribution.sessionId,
                targetType = InformationRequestAuditTargets.REQUEST,
                targetId = read.requestId.toString(),
                payload = read.payload,
                idempotencyKey = "${read.eventType.key}|${read.requestId}|${read.actor.principal.kind}:${read.actor.principal.id}|${UUID.randomUUID()}",
                businessTransactionId = read.businessTransactionId,
            ),
        )
    }
}
