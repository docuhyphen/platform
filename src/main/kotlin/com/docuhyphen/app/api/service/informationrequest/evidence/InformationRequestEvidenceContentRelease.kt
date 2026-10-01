package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceMalwareOutcome
import com.docuhyphen.app.api.model.informationrequest.evidence.malwareOutcome
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestEvidenceContentRelease @Inject constructor(
    private val assessmentRepository: InformationRequestEvidenceAssessmentRepository,
    private val deploymentPolicy: InformationRequestEvidenceDeploymentPolicy,
)
{
    fun requireReleasable(version: InformationRequestEvidenceVersion, access: RequestAccessContext)
    {
        val scan = InformationRequestEvidenceAssessmentSelection.governingScan(assessmentRepository.findForVersions(listOf(version.id)))
        val outcome = scan?.malwareOutcome()
        if (outcome == InformationRequestEvidenceMalwareOutcome.MALWARE_DETECTED)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EVIDENCE_CONTENT_QUARANTINED,
                "This evidence is quarantined because malware was detected in it",
            )
        }
        if (!deploymentPolicy.malwareScanRequired()) return
        if (outcome == InformationRequestEvidenceMalwareOutcome.CLEAN && scan.productionEligible) return
        if (version.createdByPrincipalKind == access.principal.kind && version.createdByPrincipalId == access.principal.id) return

        throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.EVIDENCE_CONTENT_NOT_RELEASED,
            "This evidence is released once it has passed a malware scan",
        )
    }
}
