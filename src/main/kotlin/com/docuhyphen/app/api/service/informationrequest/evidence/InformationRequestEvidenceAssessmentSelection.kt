package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessmentKind
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceMalwareOutcome
import com.docuhyphen.app.api.model.informationrequest.evidence.malwareOutcome

object InformationRequestEvidenceAssessmentSelection
{
    fun latestInspection(assessments: List<InformationRequestEvidenceAssessment>): InformationRequestEvidenceAssessment? =
        assessments
            .filter { it.assessmentKind == InformationRequestEvidenceAssessmentKind.CONTENT_INSPECTION }
            .maxWithOrNull(ASSESSMENT_ORDER)

    fun governingScan(assessments: List<InformationRequestEvidenceAssessment>): InformationRequestEvidenceAssessment?
    {
        val scans = assessments.filter { it.assessmentKind == InformationRequestEvidenceAssessmentKind.MALWARE_SCAN }
        return scans.filter { it.malwareOutcome() == InformationRequestEvidenceMalwareOutcome.MALWARE_DETECTED }
            .maxWithOrNull(ASSESSMENT_ORDER)
            ?: scans.filter { it.malwareOutcome().settled }.maxWithOrNull(ASSESSMENT_ORDER)
            ?: scans.maxWithOrNull(ASSESSMENT_ORDER)
    }

    private val ASSESSMENT_ORDER = compareBy<InformationRequestEvidenceAssessment>({ it.assessedAt }, { it.createdAt })
}
