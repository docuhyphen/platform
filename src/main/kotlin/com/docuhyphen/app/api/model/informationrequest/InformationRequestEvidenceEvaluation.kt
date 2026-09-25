package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttributeRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import java.util.UUID

enum class InformationRequestEvidenceFindingCode(val blocking: Boolean)
{
    NOT_INSPECTED(false),
    NOT_SCANNED(false),
    SCAN_INCOMPLETE(false),
    SCAN_NOT_PRODUCTION_ELIGIBLE(false),
    MALWARE_DETECTED(true),
    CONTENT_OPAQUE(true),
    CONTENT_CORRUPT(true),
    CONTENT_ENCRYPTED(true),
    FILE_TOO_LARGE(false),
    CONTENT_TYPE_NOT_ACCEPTED(false),
    CONTENT_TYPE_MISMATCH(false),
    PAGE_COUNT_UNKNOWN(false),
    PAGE_COUNT_OUT_OF_RANGE(false),
    ATTRIBUTE_MISSING(false),
    ATTRIBUTE_NOT_ACCEPTED(false),
    ISSUED_IN_FUTURE(false),
    ISSUE_TOO_OLD(false),
    EXPIRED(false),
    VALIDITY_TOO_SHORT(false),
    FILE_COUNT_BELOW_MINIMUM(false),
    FILE_COUNT_ABOVE_MAXIMUM(false),
    TOTAL_SIZE_ABOVE_MAXIMUM(false),
    COVERAGE_TOO_SHORT(false),
    COVERAGE_NOT_CONTINUOUS(false),
    WAIVER_NOT_PERMITTED(false),
}

data class InformationRequestEvidenceFinding(
    val code: InformationRequestEvidenceFindingCode,
    val detail: String? = null,
)

enum class InformationRequestEvidenceConformance
{
    PENDING,
    CONFORMING,
    DEFICIENT,
    QUARANTINED,
    CORRUPT,
    EXPIRED,
}

enum class InformationRequestEvidenceStanding
{
    CURRENT,
    SUPERSEDED,
    WITHDRAWN,
    REMOVED,
}

enum class InformationRequestEvidenceCapturedAttribute
{
    ISSUER,
    JURISDICTION,
    LANGUAGE,
    ISSUE_DATE,
    EXPIRY_DATE,
    COVERAGE_PERIOD,
    CERTIFICATION,
    SIGNATURE,
}

enum class InformationRequestEvidenceMalwareOutcome(val settled: Boolean)
{
    CLEAN(true),
    MALWARE_DETECTED(true),
    ERROR(false),
    TIMEOUT(false),
    UNAVAILABLE(false),
    STALE_SIGNATURES(false),
    SKIPPED(false),
    INDETERMINATE(false),
}

enum class InformationRequestEvidenceRequirementState(val completesWork: Boolean)
{
    NOT_PROVIDED(false),
    PENDING_ASSESSMENT(false),
    INCOMPLETE(false),
    DEFICIENT(false),
    REVIEWABLE(true),
    SATISFIED(true),
    WAIVED(true),
    WAIVER_REQUESTED(true),
}

data class InformationRequestEvidenceInspectionFacts(
    val detectedMediaType: String?,
    val pageCount: Int?,
    val encrypted: Boolean,
    val corrupt: Boolean,
)

data class InformationRequestEvidenceMalwareFacts(
    val outcome: InformationRequestEvidenceMalwareOutcome,
    val productionEligible: Boolean,
)

data class InformationRequestEvidenceVersionFacts(
    val versionId: UUID,
    val artifactId: UUID,
    val versionNumber: Int,
    val standing: InformationRequestEvidenceStanding,
    val fileBacked: Boolean,
    val contentVerification: DocumentVersionContentVerification?,
    val contentLength: Long?,
    val declaredMediaType: String?,
    val attributes: InformationRequestEvidenceAttributes,
    val inspection: InformationRequestEvidenceInspectionFacts?,
    val malware: InformationRequestEvidenceMalwareFacts?,
)

data class InformationRequestEvidencePolicy(
    val minimumFileCount: Int,
    val maximumFileCount: Int?,
    val maximumFileSizeBytes: Long?,
    val maximumTotalSizeBytes: Long?,
    val minimumPageCount: Int?,
    val maximumPageCount: Int?,
    val attributeRequirements: Map<InformationRequestEvidenceCapturedAttribute, InformationRequestEvidenceAttributeRequirement>,
    val acceptedValues: Map<InformationRequestEvidenceAttribute, Set<String>>,
    val maximumIssueAgeDays: Int?,
    val minimumRemainingValidityDays: Int?,
    val minimumCoverageDays: Int?,
    val coverageContinuityRequired: Boolean,
    val waiverPolicy: InformationRequestEvidenceWaiverPolicy,
    val conformancePolicy: InformationRequestEvidenceConformancePolicy,
)
{
    fun requirementOf(attribute: InformationRequestEvidenceCapturedAttribute): InformationRequestEvidenceAttributeRequirement =
        attributeRequirements[attribute] ?: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED
}

data class InformationRequestEvidenceVersionEvaluation(
    val versionId: UUID,
    val artifactId: UUID,
    val standing: InformationRequestEvidenceStanding,
    val conformance: InformationRequestEvidenceConformance,
    val findings: List<InformationRequestEvidenceFinding>,
)

data class InformationRequestEvidenceRequirementEvaluation(
    val state: InformationRequestEvidenceRequirementState,
    val findings: List<InformationRequestEvidenceFinding>,
    val versions: List<InformationRequestEvidenceVersionEvaluation>,
    val satisfiedBySubstitute: Boolean,
)
