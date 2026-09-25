package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttributeRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFindingCode.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object InformationRequestEvidencePolicyEvaluator
{
    private val PENDING_CODES = setOf(NOT_INSPECTED, NOT_SCANNED, SCAN_INCOMPLETE, SCAN_NOT_PRODUCTION_ELIGIBLE)
    private const val UNDECLARED_MEDIA_TYPE = "application/octet-stream"

    fun evaluateVersion(
        policy: InformationRequestEvidencePolicy,
        facts: InformationRequestEvidenceVersionFacts,
        asOf: LocalDate,
        malwareScanRequired: Boolean = true,
    ): InformationRequestEvidenceVersionEvaluation
    {
        val findings = mutableListOf<InformationRequestEvidenceFinding>()
        if (facts.fileBacked)
        {
            findings += contentFindings(policy, facts, malwareScanRequired)
        }
        findings += attributeFindings(policy, facts.attributes, asOf)

        return InformationRequestEvidenceVersionEvaluation(
            versionId = facts.versionId,
            artifactId = facts.artifactId,
            standing = facts.standing,
            conformance = conformanceOf(findings),
            findings = findings,
        )
    }

    fun evaluateRequirement(
        policy: InformationRequestEvidencePolicy,
        versions: List<InformationRequestEvidenceVersionFacts>,
        asOf: LocalDate,
        substituteSatisfied: Boolean = false,
        disposition: InformationRequestResponseDisposition? = null,
        malwareScanRequired: Boolean = true,
    ): InformationRequestEvidenceRequirementEvaluation
    {
        val evaluations = versions.map { evaluateVersion(policy, it, asOf, malwareScanRequired) }
        val current = evaluations.filter { it.standing == InformationRequestEvidenceStanding.CURRENT }
        val currentFacts = versions.filter { it.standing == InformationRequestEvidenceStanding.CURRENT }
        val conforming = current.filter { it.conformance == InformationRequestEvidenceConformance.CONFORMING }
        val findings = mutableListOf<InformationRequestEvidenceFinding>()

        policy.maximumFileCount?.let { maximum ->
            if (current.size > maximum) findings += finding(FILE_COUNT_ABOVE_MAXIMUM, "$maximum")
        }
        policy.maximumTotalSizeBytes?.let { maximum ->
            if (currentFacts.filter { it.fileBacked }.sumOf { it.contentLength ?: 0 } > maximum)
            {
                findings += finding(TOTAL_SIZE_ABOVE_MAXIMUM, "$maximum")
            }
        }
        if (conforming.isNotEmpty())
        {
            findings += coverageFindings(policy, currentFacts.filter { facts -> conforming.any { it.versionId == facts.versionId } })
        }
        if (current.isNotEmpty() && conforming.size < policy.minimumFileCount)
        {
            findings += finding(FILE_COUNT_BELOW_MINIMUM, "${policy.minimumFileCount}")
        }

        if (disposition == InformationRequestResponseDisposition.WAIVED)
        {
            when (policy.waiverPolicy)
            {
                InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED ->
                    return result(InformationRequestEvidenceRequirementState.WAIVED, findings, evaluations)
                InformationRequestEvidenceWaiverPolicy.REVIEW_APPROVAL_REQUIRED ->
                    return result(InformationRequestEvidenceRequirementState.WAIVER_REQUESTED, findings, evaluations)
                InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED ->
                    findings += finding(WAIVER_NOT_PERMITTED)
            }
        }

        val requirementDeficiencies = findings.filter {
            it.code != FILE_COUNT_BELOW_MINIMUM && it.code != WAIVER_NOT_PERMITTED
        }
        val everyCurrentFileConforms = conforming.size == current.size
        if (everyCurrentFileConforms && conforming.size >= policy.minimumFileCount && requirementDeficiencies.isEmpty())
        {
            return result(InformationRequestEvidenceRequirementState.SATISFIED, findings, evaluations)
        }
        if (substituteSatisfied && everyCurrentFileConforms)
        {
            return result(InformationRequestEvidenceRequirementState.SATISFIED, findings, evaluations, bySubstitute = true)
        }
        if (current.isEmpty())
        {
            return result(InformationRequestEvidenceRequirementState.NOT_PROVIDED, findings, evaluations)
        }

        val deficient = current.filter {
            it.conformance != InformationRequestEvidenceConformance.CONFORMING &&
                it.conformance != InformationRequestEvidenceConformance.PENDING
        }
        val pending = current.filter { it.conformance == InformationRequestEvidenceConformance.PENDING }
        if (pending.isNotEmpty() && deficient.isEmpty() && requirementDeficiencies.isEmpty())
        {
            return result(InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT, findings, evaluations)
        }

        val reviewable = policy.conformancePolicy == InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE &&
            pending.isEmpty() &&
            deficient.all { version -> version.conformance == InformationRequestEvidenceConformance.DEFICIENT } &&
            deficient.none { version -> version.findings.any { it.code.excludesReview() } } &&
            conforming.size + deficient.size >= policy.minimumFileCount
        if (reviewable)
        {
            return result(InformationRequestEvidenceRequirementState.REVIEWABLE, findings, evaluations)
        }
        if (deficient.isNotEmpty() || requirementDeficiencies.isNotEmpty())
        {
            return result(InformationRequestEvidenceRequirementState.DEFICIENT, findings, evaluations)
        }
        if (pending.isNotEmpty())
        {
            return result(InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT, findings, evaluations)
        }
        return result(InformationRequestEvidenceRequirementState.INCOMPLETE, findings, evaluations)
    }

    private fun contentFindings(
        policy: InformationRequestEvidencePolicy,
        facts: InformationRequestEvidenceVersionFacts,
        malwareScanRequired: Boolean,
    ): List<InformationRequestEvidenceFinding>
    {
        val findings = mutableListOf<InformationRequestEvidenceFinding>()
        val malware = facts.malware
        when
        {
            malware?.outcome == InformationRequestEvidenceMalwareOutcome.MALWARE_DETECTED -> findings += finding(MALWARE_DETECTED)
            !malwareScanRequired -> Unit
            malware == null -> findings += finding(NOT_SCANNED)
            !malware.outcome.settled -> findings += finding(SCAN_INCOMPLETE, malware.outcome.name)
            !malware.productionEligible -> findings += finding(SCAN_NOT_PRODUCTION_ELIGIBLE)
        }
        if (facts.contentVerification != DocumentVersionContentVerification.VERIFIED)
        {
            findings += finding(CONTENT_OPAQUE)
        }
        policy.maximumFileSizeBytes?.let { maximum ->
            if ((facts.contentLength ?: 0) > maximum) findings += finding(FILE_TOO_LARGE, "$maximum")
        }

        val inspection = facts.inspection
        if (inspection == null)
        {
            findings += finding(NOT_INSPECTED)
            return findings
        }
        if (inspection.corrupt) findings += finding(CONTENT_CORRUPT)
        if (inspection.encrypted) findings += finding(CONTENT_ENCRYPTED)

        val detected = inspection.detectedMediaType?.let(InformationRequestEvidenceMediaTypes::canonical)
        val accepted = policy.acceptedValues[InformationRequestEvidenceAttribute.CONTENT_TYPE].orEmpty()
            .map(InformationRequestEvidenceMediaTypes::canonical)
            .toSet()
        if (accepted.isNotEmpty() && (detected == null || detected !in accepted))
        {
            findings += finding(CONTENT_TYPE_NOT_ACCEPTED, detected)
        }
        val declared = facts.declaredMediaType?.let(InformationRequestEvidenceMediaTypes::canonical)?.takeIf { it != UNDECLARED_MEDIA_TYPE }
        if (declared != null && detected != null && declared != detected)
        {
            findings += finding(CONTENT_TYPE_MISMATCH, declared)
        }

        if (policy.minimumPageCount != null || policy.maximumPageCount != null)
        {
            val pages = inspection.pageCount
            when
            {
                pages == null -> findings += finding(PAGE_COUNT_UNKNOWN)
                policy.minimumPageCount?.let { pages < it } == true ||
                    policy.maximumPageCount?.let { pages > it } == true ->
                    findings += finding(PAGE_COUNT_OUT_OF_RANGE, "$pages")
            }
        }
        return findings
    }

    private fun attributeFindings(
        policy: InformationRequestEvidencePolicy,
        attributes: InformationRequestEvidenceAttributes,
        asOf: LocalDate,
    ): List<InformationRequestEvidenceFinding>
    {
        val findings = mutableListOf<InformationRequestEvidenceFinding>()
        listOf(
            InformationRequestEvidenceCapturedAttribute.ISSUER to (attributes.issuer != null),
            InformationRequestEvidenceCapturedAttribute.JURISDICTION to (attributes.jurisdiction != null),
            InformationRequestEvidenceCapturedAttribute.LANGUAGE to (attributes.language != null),
            InformationRequestEvidenceCapturedAttribute.ISSUE_DATE to (attributes.issuedOn != null),
            InformationRequestEvidenceCapturedAttribute.EXPIRY_DATE to (attributes.expiresOn != null),
            InformationRequestEvidenceCapturedAttribute.COVERAGE_PERIOD to (attributes.coverage != null),
            InformationRequestEvidenceCapturedAttribute.CERTIFICATION to (attributes.certificationReference != null),
            InformationRequestEvidenceCapturedAttribute.SIGNATURE to (attributes.signatureReference != null),
        ).forEach { (attribute, stated) ->
            if (policy.requirementOf(attribute) == InformationRequestEvidenceAttributeRequirement.REQUIRED && !stated)
            {
                findings += finding(ATTRIBUTE_MISSING, attribute.name)
            }
        }
        listOf(
            InformationRequestEvidenceAttribute.ISSUER to attributes.issuer,
            InformationRequestEvidenceAttribute.JURISDICTION to attributes.jurisdiction,
            InformationRequestEvidenceAttribute.LANGUAGE to attributes.language,
        ).forEach { (attribute, value) ->
            val accepted = policy.acceptedValues[attribute].orEmpty()
            if (value != null && accepted.isNotEmpty() && accepted.none { it.equals(value, ignoreCase = true) })
            {
                findings += finding(ATTRIBUTE_NOT_ACCEPTED, attribute.name)
            }
        }
        attributes.issuedOn?.let { issuedOn ->
            if (issuedOn.isAfter(asOf)) findings += finding(ISSUED_IN_FUTURE)
            policy.maximumIssueAgeDays?.let { maximumAge ->
                if (ChronoUnit.DAYS.between(issuedOn, asOf) > maximumAge) findings += finding(ISSUE_TOO_OLD, "$maximumAge")
            }
        }
        attributes.expiresOn?.let { expiresOn ->
            if (expiresOn.isBefore(asOf))
            {
                findings += finding(EXPIRED)
            }
            else
            {
                policy.minimumRemainingValidityDays?.let { minimum ->
                    if (ChronoUnit.DAYS.between(asOf, expiresOn) < minimum) findings += finding(VALIDITY_TOO_SHORT, "$minimum")
                }
            }
        }
        return findings
    }

    private fun coverageFindings(
        policy: InformationRequestEvidencePolicy,
        conforming: List<InformationRequestEvidenceVersionFacts>,
    ): List<InformationRequestEvidenceFinding>
    {
        if (policy.minimumCoverageDays == null && !policy.coverageContinuityRequired) return emptyList()

        val merged = mutableListOf<InformationRequestEvidenceCoverage>()
        conforming.mapNotNull { it.attributes.coverage }.sortedBy { it.startsOn }.forEach { period ->
            val last = merged.lastOrNull()
            if (last != null && !period.startsOn.isAfter(last.endsOn.plusDays(1)))
            {
                merged[merged.lastIndex] = InformationRequestEvidenceCoverage(last.startsOn, maxOf(last.endsOn, period.endsOn))
            }
            else
            {
                merged += period
            }
        }

        val findings = mutableListOf<InformationRequestEvidenceFinding>()
        val coveredDays = merged.sumOf { it.days() }
        policy.minimumCoverageDays?.let { minimum ->
            if (coveredDays < minimum) findings += finding(COVERAGE_TOO_SHORT, "$coveredDays")
        }
        if (policy.coverageContinuityRequired && merged.size > 1)
        {
            findings += finding(COVERAGE_NOT_CONTINUOUS, "${merged.size - 1}")
        }
        return findings
    }

    private fun conformanceOf(findings: List<InformationRequestEvidenceFinding>): InformationRequestEvidenceConformance =
        when
        {
            findings.any { it.code == MALWARE_DETECTED } -> InformationRequestEvidenceConformance.QUARANTINED
            findings.any { it.code == CONTENT_CORRUPT } -> InformationRequestEvidenceConformance.CORRUPT
            findings.any { it.code !in PENDING_CODES && it.code != EXPIRED } -> InformationRequestEvidenceConformance.DEFICIENT
            findings.any { it.code == EXPIRED } -> InformationRequestEvidenceConformance.EXPIRED
            findings.any { it.code in PENDING_CODES } -> InformationRequestEvidenceConformance.PENDING
            else -> InformationRequestEvidenceConformance.CONFORMING
        }

    private fun result(
        state: InformationRequestEvidenceRequirementState,
        findings: List<InformationRequestEvidenceFinding>,
        versions: List<InformationRequestEvidenceVersionEvaluation>,
        bySubstitute: Boolean = false,
    ) = InformationRequestEvidenceRequirementEvaluation(state, findings, versions, bySubstitute)

    private fun finding(code: InformationRequestEvidenceFindingCode, detail: String? = null) =
        InformationRequestEvidenceFinding(code, detail)

    private fun InformationRequestEvidenceFindingCode.excludesReview(): Boolean =
        blocking || this in PENDING_CODES || this == EXPIRED
}
