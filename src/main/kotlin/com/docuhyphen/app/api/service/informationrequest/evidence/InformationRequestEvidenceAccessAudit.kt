package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceRelease
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.informationRequestAuditOwner
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestEvidenceAccessAudit @Inject constructor(
    private val auditRecorder: AuditRecorder,
    private val actorLabels: InformationRequestAuditActorLabelResolver,
)
{
    fun record(release: InformationRequestEvidenceRelease, use: InformationRequestEvidenceContentUse)
    {
        val attribution = actorLabels.attribute(InformationRequestAuditActor.of(release.access))
        val retained = release.retained
        auditRecorder.record(
            AuditEventDraft(
                owner = informationRequestAuditOwner(release.request),
                eventTypeKey = when (use)
                {
                    InformationRequestEvidenceContentUse.DOWNLOAD -> AuditEventType.INFORMATION_REQUEST_EVIDENCE_DOWNLOAD.key
                    InformationRequestEvidenceContentUse.PREVIEW -> AuditEventType.INFORMATION_REQUEST_EVIDENCE_PREVIEW.key
                },
                outcome = AuditOutcome.SUCCESS,
                actorId = attribution.actorId,
                actorKind = attribution.actorKind,
                actorLabel = attribution.actorLabel,
                sessionId = attribution.sessionId,
                targetType = "INFORMATION_REQUEST",
                targetId = release.request.id.toString(),
                payload = mapOf(
                    "requirementId" to release.requirementId.toString(),
                    "evidenceArtifactId" to release.version.evidenceArtifactId.toString(),
                    "evidenceVersionNumber" to release.version.versionNumber.toString(),
                ) + if (retained == null) emptyMap() else mapOf(
                    "submissionPackageId" to retained.packageId.toString(),
                    "submissionItemId" to retained.submissionItemId.toString(),
                ),
            ),
        )
    }
}
