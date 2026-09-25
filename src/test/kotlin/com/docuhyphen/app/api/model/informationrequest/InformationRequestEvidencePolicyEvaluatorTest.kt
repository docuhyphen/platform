package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttributeRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class InformationRequestEvidencePolicyEvaluatorTest
{
    private val asOf = LocalDate.of(2026, 9, 25)

    @Test
    fun `a verified, inspected, production-clean file within policy conforms`()
    {
        val evaluation = InformationRequestEvidencePolicyEvaluator.evaluateVersion(policy(), file(), asOf)

        assertEquals(InformationRequestEvidenceConformance.CONFORMING, evaluation.conformance)
        assertTrue(evaluation.findings.isEmpty())
    }

    @Test
    fun `a file is pending until it has been inspected and scanned by a production-eligible engine`()
    {
        assertPending(file(inspection = null), InformationRequestEvidenceFindingCode.NOT_INSPECTED)
        assertPending(file(malware = null), InformationRequestEvidenceFindingCode.NOT_SCANNED)
        listOf(
            InformationRequestEvidenceMalwareOutcome.ERROR,
            InformationRequestEvidenceMalwareOutcome.TIMEOUT,
            InformationRequestEvidenceMalwareOutcome.UNAVAILABLE,
            InformationRequestEvidenceMalwareOutcome.STALE_SIGNATURES,
            InformationRequestEvidenceMalwareOutcome.SKIPPED,
            InformationRequestEvidenceMalwareOutcome.INDETERMINATE,
        ).forEach { outcome ->
            assertPending(file(malware = InformationRequestEvidenceMalwareFacts(outcome, true)), InformationRequestEvidenceFindingCode.SCAN_INCOMPLETE)
        }
        assertPending(
            file(malware = InformationRequestEvidenceMalwareFacts(InformationRequestEvidenceMalwareOutcome.CLEAN, false)),
            InformationRequestEvidenceFindingCode.SCAN_NOT_PRODUCTION_ELIGIBLE,
        )
    }

    @Test
    fun `where the deployment does not require a malware scan, a file conforms on its other checks without one`()
    {
        listOf(
            null,
            InformationRequestEvidenceMalwareFacts(InformationRequestEvidenceMalwareOutcome.ERROR, false),
            InformationRequestEvidenceMalwareFacts(InformationRequestEvidenceMalwareOutcome.UNAVAILABLE, false),
            InformationRequestEvidenceMalwareFacts(InformationRequestEvidenceMalwareOutcome.CLEAN, false),
        ).forEach { malware ->
            val evaluation = InformationRequestEvidencePolicyEvaluator.evaluateVersion(
                policy(),
                file(malware = malware),
                asOf,
                malwareScanRequired = false,
            )

            assertEquals(InformationRequestEvidenceConformance.CONFORMING, evaluation.conformance, "for $malware")
            assertTrue(evaluation.findings.isEmpty(), "expected no findings for $malware but was ${evaluation.findings}")
        }
        assertPending(file(inspection = null), InformationRequestEvidenceFindingCode.NOT_INSPECTED, malwareScanRequired = false)
    }

    @Test
    fun `detected malware quarantines a file even where the deployment does not require a scan`()
    {
        val evaluation = InformationRequestEvidencePolicyEvaluator.evaluateVersion(
            policy(),
            file(malware = InformationRequestEvidenceMalwareFacts(InformationRequestEvidenceMalwareOutcome.MALWARE_DETECTED, true)),
            asOf,
            malwareScanRequired = false,
        )

        assertEquals(InformationRequestEvidenceConformance.QUARANTINED, evaluation.conformance)
    }

    @Test
    fun `where the deployment does not require a malware scan, unscanned conforming files satisfy the Requirement`()
    {
        val evaluation = InformationRequestEvidencePolicyEvaluator.evaluateRequirement(
            policy(minimumFiles = 2),
            listOf(file(malware = null), file(malware = null)),
            asOf,
            malwareScanRequired = false,
        )

        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, evaluation.state)
        assertEquals(
            InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT,
            InformationRequestEvidencePolicyEvaluator.evaluateRequirement(policy(), listOf(file(malware = null)), asOf).state,
        )
    }

    @Test
    fun `detected malware quarantines the file whatever else is true of it`()
    {
        val evaluation = InformationRequestEvidencePolicyEvaluator.evaluateVersion(
            policy(),
            file(malware = InformationRequestEvidenceMalwareFacts(InformationRequestEvidenceMalwareOutcome.MALWARE_DETECTED, true)),
            asOf,
        )

        assertEquals(InformationRequestEvidenceConformance.QUARANTINED, evaluation.conformance)
        assertTrue(evaluation.findings.single { it.code == InformationRequestEvidenceFindingCode.MALWARE_DETECTED }.code.blocking)
    }

    @Test
    fun `opaque ciphertext is never conforming and never reviewable`()
    {
        val evaluation = InformationRequestEvidencePolicyEvaluator.evaluateVersion(
            policy(conformance = InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE),
            file(verification = DocumentVersionContentVerification.UNVERIFIED),
            asOf,
        )

        assertEquals(InformationRequestEvidenceConformance.DEFICIENT, evaluation.conformance)
        val finding = evaluation.findings.single { it.code == InformationRequestEvidenceFindingCode.CONTENT_OPAQUE }
        assertTrue(finding.code.blocking)
    }

    @Test
    fun `corrupt and password-protected content are technical failures`()
    {
        val corrupt = InformationRequestEvidencePolicyEvaluator.evaluateVersion(
            policy(),
            file(inspection = inspected(corrupt = true)),
            asOf,
        )
        assertEquals(InformationRequestEvidenceConformance.CORRUPT, corrupt.conformance)

        val encrypted = InformationRequestEvidencePolicyEvaluator.evaluateVersion(
            policy(),
            file(inspection = inspected(encrypted = true)),
            asOf,
        )
        assertEquals(InformationRequestEvidenceConformance.DEFICIENT, encrypted.conformance)
        assertTrue(encrypted.findings.single().code == InformationRequestEvidenceFindingCode.CONTENT_ENCRYPTED)
        assertTrue(encrypted.findings.single().code.blocking)
    }

    @Test
    fun `size, detected content type, and page bounds are checked against the policy`()
    {
        val limited = policy(maximumFileSize = 100, minimumPages = 2, maximumPages = 5, acceptedTypes = setOf("application/pdf"))

        assertFinding(limited, file(length = 101), InformationRequestEvidenceFindingCode.FILE_TOO_LARGE)
        assertFinding(limited, file(inspection = inspected(type = "image/png")), InformationRequestEvidenceFindingCode.CONTENT_TYPE_NOT_ACCEPTED)
        assertFinding(limited, file(declaredType = "image/png"), InformationRequestEvidenceFindingCode.CONTENT_TYPE_MISMATCH)
        assertFinding(limited, file(inspection = inspected(pages = 1)), InformationRequestEvidenceFindingCode.PAGE_COUNT_OUT_OF_RANGE)
        assertFinding(limited, file(inspection = inspected(pages = 6)), InformationRequestEvidenceFindingCode.PAGE_COUNT_OUT_OF_RANGE)
        assertFinding(limited, file(inspection = inspected(type = "image/png", pages = null), declaredType = "image/png"), InformationRequestEvidenceFindingCode.PAGE_COUNT_UNKNOWN)
        assertEquals(
            InformationRequestEvidenceConformance.CONFORMING,
            InformationRequestEvidencePolicyEvaluator.evaluateVersion(limited, file(declaredType = "application/pdf; charset=binary"), asOf).conformance,
        )
    }

    @Test
    fun `required attributes, accepted values, freshness, and validity are checked as of the evaluation date`()
    {
        val strict = policy(
            requirements = mapOf(
                InformationRequestEvidenceCapturedAttribute.ISSUER to InformationRequestEvidenceAttributeRequirement.REQUIRED,
                InformationRequestEvidenceCapturedAttribute.ISSUE_DATE to InformationRequestEvidenceAttributeRequirement.REQUIRED,
                InformationRequestEvidenceCapturedAttribute.EXPIRY_DATE to InformationRequestEvidenceAttributeRequirement.REQUIRED,
                InformationRequestEvidenceCapturedAttribute.SIGNATURE to InformationRequestEvidenceAttributeRequirement.REQUIRED,
            ),
            acceptedIssuers = setOf("Process Registry"),
            maximumIssueAgeDays = 120,
            minimumRemainingValidityDays = 15,
        )
        val stated = InformationRequestEvidenceAttributes(
            issuer = "Process Registry",
            issuedOn = asOf.minusDays(10),
            expiresOn = asOf.plusDays(60),
            signatureReference = "SIG-7",
        )

        assertEquals(
            InformationRequestEvidenceConformance.CONFORMING,
            InformationRequestEvidencePolicyEvaluator.evaluateVersion(strict, file(attributes = stated), asOf).conformance,
        )
        assertFinding(strict, file(attributes = stated.copy(signatureReference = null)), InformationRequestEvidenceFindingCode.ATTRIBUTE_MISSING)
        assertFinding(strict, file(attributes = stated.copy(issuer = "Other Registry")), InformationRequestEvidenceFindingCode.ATTRIBUTE_NOT_ACCEPTED)
        assertFinding(strict, file(attributes = stated.copy(issuedOn = asOf.minusDays(121))), InformationRequestEvidenceFindingCode.ISSUE_TOO_OLD)
        assertFinding(strict, file(attributes = stated.copy(issuedOn = asOf.plusDays(1))), InformationRequestEvidenceFindingCode.ISSUED_IN_FUTURE)
        assertFinding(strict, file(attributes = stated.copy(expiresOn = asOf.plusDays(14))), InformationRequestEvidenceFindingCode.VALIDITY_TOO_SHORT)

        val expired = InformationRequestEvidencePolicyEvaluator.evaluateVersion(
            strict,
            file(attributes = stated.copy(expiresOn = asOf.minusDays(1))),
            asOf,
        )
        assertEquals(InformationRequestEvidenceConformance.EXPIRED, expired.conformance)
    }

    @Test
    fun `an external reference is judged on its captured attributes alone`()
    {
        val evaluation = InformationRequestEvidencePolicyEvaluator.evaluateVersion(
            policy(requirements = mapOf(InformationRequestEvidenceCapturedAttribute.ISSUER to InformationRequestEvidenceAttributeRequirement.REQUIRED)),
            external(InformationRequestEvidenceAttributes(issuer = "Process Registry")),
            asOf,
        )

        assertEquals(InformationRequestEvidenceConformance.CONFORMING, evaluation.conformance)
    }

    @Test
    fun `a Requirement is satisfied only by enough conforming current files`()
    {
        val twoFiles = policy(minimumFiles = 2)

        assertEquals(
            InformationRequestEvidenceRequirementState.NOT_PROVIDED,
            evaluate(twoFiles, emptyList()).state,
        )
        assertEquals(
            InformationRequestEvidenceRequirementState.INCOMPLETE,
            evaluate(twoFiles, listOf(file())).state,
        )
        assertEquals(
            InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT,
            evaluate(twoFiles, listOf(file(), file(malware = null))).state,
        )
        assertEquals(
            InformationRequestEvidenceRequirementState.SATISFIED,
            evaluate(twoFiles, listOf(file(), file())).state,
        )
    }

    @Test
    fun `superseded, withdrawn, and removed versions keep their own state but never count`()
    {
        val versions = listOf(
            file(standing = InformationRequestEvidenceStanding.SUPERSEDED),
            file(standing = InformationRequestEvidenceStanding.WITHDRAWN),
            file(standing = InformationRequestEvidenceStanding.REMOVED),
            file(
                standing = InformationRequestEvidenceStanding.SUPERSEDED,
                malware = InformationRequestEvidenceMalwareFacts(InformationRequestEvidenceMalwareOutcome.MALWARE_DETECTED, true),
            ),
        )

        val evaluation = evaluate(policy(), versions)

        assertEquals(InformationRequestEvidenceRequirementState.NOT_PROVIDED, evaluation.state)
        assertEquals(
            listOf(
                InformationRequestEvidenceConformance.CONFORMING,
                InformationRequestEvidenceConformance.CONFORMING,
                InformationRequestEvidenceConformance.CONFORMING,
                InformationRequestEvidenceConformance.QUARANTINED,
            ),
            evaluation.versions.map { it.conformance },
        )
    }

    @Test
    fun `a deficient current file makes the Requirement deficient unless deficiencies are reviewable`()
    {
        val wrongType = file(inspection = inspected(type = "image/png"), declaredType = "image/png")

        assertEquals(
            InformationRequestEvidenceRequirementState.DEFICIENT,
            evaluate(policy(acceptedTypes = setOf("application/pdf")), listOf(wrongType)).state,
        )
        assertEquals(
            InformationRequestEvidenceRequirementState.REVIEWABLE,
            evaluate(
                policy(acceptedTypes = setOf("application/pdf"), conformance = InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE),
                listOf(wrongType),
            ).state,
        )
        assertEquals(
            InformationRequestEvidenceRequirementState.DEFICIENT,
            evaluate(
                policy(conformance = InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE),
                listOf(file(verification = DocumentVersionContentVerification.UNVERIFIED)),
            ).state,
        )
    }

    @Test
    fun `a deficient file that is unscanned or expired is never offered for review`()
    {
        val reviewablePolicy = policy(
            acceptedTypes = setOf("application/pdf"),
            conformance = InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE,
        )
        val unscannedWrongType = file(inspection = inspected(type = "image/png"), declaredType = "image/png", malware = null)
        val expired = file(attributes = InformationRequestEvidenceAttributes(expiresOn = asOf.minusDays(3)))
        val expiredWrongType = file(
            inspection = inspected(type = "image/png"),
            declaredType = "image/png",
            attributes = InformationRequestEvidenceAttributes(expiresOn = asOf.minusDays(3)),
        )

        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, evaluate(reviewablePolicy, listOf(unscannedWrongType)).state)
        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, evaluate(reviewablePolicy, listOf(expired)).state)
        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, evaluate(reviewablePolicy, listOf(expiredWrongType)).state)
    }

    @Test
    fun `every current file must conform before enough conforming files satisfy the Requirement`()
    {
        val quarantined = file(malware = InformationRequestEvidenceMalwareFacts(InformationRequestEvidenceMalwareOutcome.MALWARE_DETECTED, true))
        val wrongType = file(inspection = inspected(type = "image/png"), declaredType = "image/png")

        assertEquals(
            InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT,
            evaluate(policy(), listOf(file(), file(malware = null))).state,
        )
        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, evaluate(policy(), listOf(file(), quarantined)).state)
        assertEquals(
            InformationRequestEvidenceRequirementState.DEFICIENT,
            evaluate(policy(acceptedTypes = setOf("application/pdf")), listOf(file(), wrongType)).state,
        )
        assertEquals(
            InformationRequestEvidenceRequirementState.DEFICIENT,
            InformationRequestEvidencePolicyEvaluator.evaluateRequirement(policy(), listOf(quarantined), asOf, substituteSatisfied = true).state,
        )
    }

    @Test
    fun `too many files or too many bytes are Requirement-level findings`()
    {
        val bounded = policy(maximumFiles = 2, maximumTotalSize = 150)

        val tooMany = evaluate(bounded, listOf(file(length = 10), file(length = 10), file(length = 10)))
        assertTrue(tooMany.findings.any { it.code == InformationRequestEvidenceFindingCode.FILE_COUNT_ABOVE_MAXIMUM })
        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, tooMany.state)

        val tooLarge = evaluate(bounded, listOf(file(length = 100), file(length = 100)))
        assertTrue(tooLarge.findings.any { it.code == InformationRequestEvidenceFindingCode.TOTAL_SIZE_ABOVE_MAXIMUM })
    }

    @Test
    fun `several files can cover several periods, continuously when the policy requires it`()
    {
        val coverage = policy(
            requirements = mapOf(InformationRequestEvidenceCapturedAttribute.COVERAGE_PERIOD to InformationRequestEvidenceAttributeRequirement.REQUIRED),
            minimumCoverageDays = 45,
            coverageContinuityRequired = true,
        )
        val january = covering(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        val february = covering(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))
        val april = covering(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30))

        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, evaluate(coverage, listOf(february, january)).state)
        val gapped = evaluate(coverage, listOf(january, april))
        assertTrue(gapped.findings.any { it.code == InformationRequestEvidenceFindingCode.COVERAGE_NOT_CONTINUOUS })
        val short = evaluate(coverage, listOf(january))
        assertTrue(short.findings.any { it.code == InformationRequestEvidenceFindingCode.COVERAGE_TOO_SHORT })
        assertEquals(
            InformationRequestEvidenceRequirementState.SATISFIED,
            evaluate(coverage.copy(coverageContinuityRequired = false), listOf(january, april)).state,
        )
    }

    @Test
    fun `a satisfied substitute satisfies the Requirement it stands in for`()
    {
        val evaluation = InformationRequestEvidencePolicyEvaluator.evaluateRequirement(
            policy(),
            emptyList(),
            asOf,
            substituteSatisfied = true,
        )

        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, evaluation.state)
        assertTrue(evaluation.satisfiedBySubstitute)
    }

    @Test
    fun `a waiver follows the policy's waiver rule`()
    {
        fun waived(waiverPolicy: InformationRequestEvidenceWaiverPolicy) =
            InformationRequestEvidencePolicyEvaluator.evaluateRequirement(
                policy(waiver = waiverPolicy),
                emptyList(),
                asOf,
                disposition = InformationRequestResponseDisposition.WAIVED,
            )

        assertEquals(InformationRequestEvidenceRequirementState.WAIVED, waived(InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED).state)
        assertEquals(
            InformationRequestEvidenceRequirementState.WAIVER_REQUESTED,
            waived(InformationRequestEvidenceWaiverPolicy.REVIEW_APPROVAL_REQUIRED).state,
        )
        val refused = waived(InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED)
        assertEquals(InformationRequestEvidenceRequirementState.NOT_PROVIDED, refused.state)
        assertTrue(refused.findings.any { it.code == InformationRequestEvidenceFindingCode.WAIVER_NOT_PERMITTED })
    }

    @Test
    fun `only settled Requirement states count as complete work`()
    {
        assertTrue(InformationRequestEvidenceRequirementState.SATISFIED.completesWork)
        assertTrue(InformationRequestEvidenceRequirementState.WAIVED.completesWork)
        assertTrue(InformationRequestEvidenceRequirementState.WAIVER_REQUESTED.completesWork)
        assertTrue(InformationRequestEvidenceRequirementState.REVIEWABLE.completesWork)
        assertFalse(InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT.completesWork)
        assertFalse(InformationRequestEvidenceRequirementState.DEFICIENT.completesWork)
        assertFalse(InformationRequestEvidenceRequirementState.INCOMPLETE.completesWork)
        assertFalse(InformationRequestEvidenceRequirementState.NOT_PROVIDED.completesWork)
    }

    private fun evaluate(policy: InformationRequestEvidencePolicy, versions: List<InformationRequestEvidenceVersionFacts>) =
        InformationRequestEvidencePolicyEvaluator.evaluateRequirement(policy, versions, asOf)

    private fun assertPending(
        facts: InformationRequestEvidenceVersionFacts,
        code: InformationRequestEvidenceFindingCode,
        malwareScanRequired: Boolean = true,
    )
    {
        val evaluation = InformationRequestEvidencePolicyEvaluator.evaluateVersion(policy(), facts, asOf, malwareScanRequired)
        assertEquals(InformationRequestEvidenceConformance.PENDING, evaluation.conformance, "for $code")
        assertTrue(evaluation.findings.any { it.code == code }, "expected $code in ${evaluation.findings}")
    }

    private fun assertFinding(
        policy: InformationRequestEvidencePolicy,
        facts: InformationRequestEvidenceVersionFacts,
        code: InformationRequestEvidenceFindingCode,
    )
    {
        val evaluation = InformationRequestEvidencePolicyEvaluator.evaluateVersion(policy, facts, asOf)
        assertTrue(evaluation.findings.any { it.code == code }, "expected $code in ${evaluation.findings}")
        assertEquals(InformationRequestEvidenceConformance.DEFICIENT, evaluation.conformance, "for $code")
    }

    private fun policy(
        minimumFiles: Int = 1,
        maximumFiles: Int? = null,
        maximumFileSize: Long? = null,
        maximumTotalSize: Long? = null,
        minimumPages: Int? = null,
        maximumPages: Int? = null,
        requirements: Map<InformationRequestEvidenceCapturedAttribute, InformationRequestEvidenceAttributeRequirement> = emptyMap(),
        acceptedTypes: Set<String> = emptySet(),
        acceptedIssuers: Set<String> = emptySet(),
        maximumIssueAgeDays: Int? = null,
        minimumRemainingValidityDays: Int? = null,
        minimumCoverageDays: Int? = null,
        coverageContinuityRequired: Boolean = false,
        waiver: InformationRequestEvidenceWaiverPolicy = InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED,
        conformance: InformationRequestEvidenceConformancePolicy = InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED,
    ) = InformationRequestEvidencePolicy(
        minimumFileCount = minimumFiles,
        maximumFileCount = maximumFiles,
        maximumFileSizeBytes = maximumFileSize,
        maximumTotalSizeBytes = maximumTotalSize,
        minimumPageCount = minimumPages,
        maximumPageCount = maximumPages,
        attributeRequirements = requirements,
        acceptedValues = buildMap {
            if (acceptedTypes.isNotEmpty()) put(InformationRequestEvidenceAttribute.CONTENT_TYPE, acceptedTypes)
            if (acceptedIssuers.isNotEmpty()) put(InformationRequestEvidenceAttribute.ISSUER, acceptedIssuers)
        },
        maximumIssueAgeDays = maximumIssueAgeDays,
        minimumRemainingValidityDays = minimumRemainingValidityDays,
        minimumCoverageDays = minimumCoverageDays,
        coverageContinuityRequired = coverageContinuityRequired,
        waiverPolicy = waiver,
        conformancePolicy = conformance,
    )

    private fun inspected(
        type: String = "application/pdf",
        pages: Int? = 3,
        encrypted: Boolean = false,
        corrupt: Boolean = false,
    ) = InformationRequestEvidenceInspectionFacts(type, pages, encrypted, corrupt)

    private fun file(
        standing: InformationRequestEvidenceStanding = InformationRequestEvidenceStanding.CURRENT,
        verification: DocumentVersionContentVerification = DocumentVersionContentVerification.VERIFIED,
        length: Long = 50,
        declaredType: String? = "application/pdf",
        attributes: InformationRequestEvidenceAttributes = InformationRequestEvidenceAttributes.NONE,
        inspection: InformationRequestEvidenceInspectionFacts? = inspected(),
        malware: InformationRequestEvidenceMalwareFacts? =
            InformationRequestEvidenceMalwareFacts(InformationRequestEvidenceMalwareOutcome.CLEAN, true),
    ) = InformationRequestEvidenceVersionFacts(
        versionId = UUID.randomUUID(),
        artifactId = UUID.randomUUID(),
        versionNumber = 1,
        standing = standing,
        fileBacked = true,
        contentVerification = verification,
        contentLength = length,
        declaredMediaType = declaredType,
        attributes = attributes,
        inspection = inspection,
        malware = malware,
    )

    private fun external(attributes: InformationRequestEvidenceAttributes) = InformationRequestEvidenceVersionFacts(
        versionId = UUID.randomUUID(),
        artifactId = UUID.randomUUID(),
        versionNumber = 1,
        standing = InformationRequestEvidenceStanding.CURRENT,
        fileBacked = false,
        contentVerification = null,
        contentLength = null,
        declaredMediaType = null,
        attributes = attributes,
        inspection = null,
        malware = null,
    )

    private fun covering(startsOn: LocalDate, endsOn: LocalDate) =
        file(attributes = InformationRequestEvidenceAttributes(coverage = InformationRequestEvidenceCoverage(startsOn, endsOn)))
}
