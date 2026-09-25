package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestEvidenceAccessAudit @Inject constructor(
    private val auditRecorder: AuditRecorder,
)
{
    fun record(
        request: InformationRequest,
        access: RequestAccessContext,
        use: InformationRequestEvidenceContentUse,
        version: InformationRequestEvidenceVersion,
        requirementId: UUID,
    )
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = informationRequestAuditOwner(request),
                eventTypeKey = when (use)
                {
                    InformationRequestEvidenceContentUse.DOWNLOAD -> AuditEventType.INFORMATION_REQUEST_EVIDENCE_DOWNLOAD.key
                    InformationRequestEvidenceContentUse.PREVIEW -> AuditEventType.INFORMATION_REQUEST_EVIDENCE_PREVIEW.key
                },
                outcome = AuditOutcome.SUCCESS,
                actorId = access.principal.id,
                actorKind = AuditActorKind.forPrincipal(access.principal.kind),
                targetType = "INFORMATION_REQUEST",
                targetId = request.id.toString(),
                payload = mapOf(
                    "requirementId" to requirementId.toString(),
                    "evidenceArtifactId" to version.evidenceArtifactId.toString(),
                    "evidenceVersionNumber" to version.versionNumber.toString(),
                ),
            ),
        )
    }
}
