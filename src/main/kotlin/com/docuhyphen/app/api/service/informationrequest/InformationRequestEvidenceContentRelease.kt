package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAssessmentSelection
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceMalwareOutcome
import com.docuhyphen.app.api.model.informationrequest.malwareOutcome
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceAssessmentRepository
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
