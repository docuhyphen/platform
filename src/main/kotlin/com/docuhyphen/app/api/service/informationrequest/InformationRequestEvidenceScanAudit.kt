package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceMalwareOutcome
import com.docuhyphen.app.api.model.informationrequest.malwareOutcome
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestEvidenceScanAudit @Inject constructor(
    private val auditRecorder: AuditRecorder,
    private val requestRepository: InformationRequestRepository,
)
{
    fun record(version: InformationRequestEvidenceVersion, assessment: InformationRequestEvidenceAssessment, reused: Boolean)
    {
        val request = requestRepository.findById(version.informationRequestId)
            ?: throw IllegalStateException("An evidence version belongs to an Information Request")
        val outcome = assessment.malwareOutcome()

        auditRecorder.record(
            AuditEventDraft(
                owner = informationRequestAuditOwner(request),
                eventTypeKey = AuditEventType.INFORMATION_REQUEST_EVIDENCE_SCAN.key,
                outcome = if (outcome.settled || outcome == InformationRequestEvidenceMalwareOutcome.SKIPPED) AuditOutcome.SUCCESS
                else AuditOutcome.ERROR,
                actorId = null,
                actorKind = AuditActorKind.SYSTEM,
                targetType = "INFORMATION_REQUEST",
                targetId = request.id.toString(),
                payload = listOfNotNull(
                    "evidenceArtifactId" to version.evidenceArtifactId.toString(),
                    "evidenceVersionNumber" to version.versionNumber.toString(),
                    "scanOutcome" to outcome.name,
                    "productionEligible" to assessment.productionEligible.toString(),
                    "reused" to reused.toString(),
                    assessment.engineName?.let { "engine" to it },
                    assessment.signatureVersion?.let { "signatureVersion" to it },
                ).toMap(),
                idempotencyKey = "${AuditEventType.INFORMATION_REQUEST_EVIDENCE_SCAN.key}|${assessment.id}",
            ),
        )
    }
}
