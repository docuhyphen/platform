package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessmentKind
import java.time.Duration
import java.time.Instant

enum class InformationRequestEvidenceInspectionOutcome
{
    INSPECTED,
    CORRUPT,
    ENCRYPTED,
}

data class InformationRequestEvidenceScannerEngine(
    val name: String,
    val version: String,
    val productionEligible: Boolean,
)

data class InformationRequestEvidenceSignatureState(
    val version: String,
    val publishedAt: Instant,
)

sealed interface InformationRequestEvidenceScanVerdict
{
    data object Clean : InformationRequestEvidenceScanVerdict

    data class Detected(val threat: String) : InformationRequestEvidenceScanVerdict

    data class Failed(val reason: String) : InformationRequestEvidenceScanVerdict
}

data class InformationRequestEvidenceScanSettings(
    val timeout: Duration,
    val maximumSignatureAge: Duration,
    val reuseWindow: Duration,
    val retryAfter: Duration = Duration.ofMinutes(15),
    val rescanAfter: Duration = reuseWindow,
    val batchSize: Int = 50,
)

fun InformationRequestEvidenceAssessment.malwareOutcome(): InformationRequestEvidenceMalwareOutcome
{
    require(assessmentKind == InformationRequestEvidenceAssessmentKind.MALWARE_SCAN) { "Not a malware scan assessment" }
    return InformationRequestEvidenceMalwareOutcome.valueOf(outcome)
}

fun InformationRequestEvidenceAssessment.inspectionOutcome(): InformationRequestEvidenceInspectionOutcome
{
    require(assessmentKind == InformationRequestEvidenceAssessmentKind.CONTENT_INSPECTION) { "Not a content inspection" }
    return InformationRequestEvidenceInspectionOutcome.valueOf(outcome)
}

fun InformationRequestEvidenceAssessment.inspectionFacts(): InformationRequestEvidenceInspectionFacts =
    InformationRequestEvidenceInspectionFacts(
        detectedMediaType = detectedMediaType,
        pageCount = pageCount,
        encrypted = inspectionOutcome() == InformationRequestEvidenceInspectionOutcome.ENCRYPTED,
        corrupt = inspectionOutcome() == InformationRequestEvidenceInspectionOutcome.CORRUPT,
    )

fun InformationRequestEvidenceAssessment.malwareFacts(): InformationRequestEvidenceMalwareFacts =
    InformationRequestEvidenceMalwareFacts(malwareOutcome(), productionEligible)

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
