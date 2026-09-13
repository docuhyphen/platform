package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * The policy the files answering one requested Document are judged by, as stated by one
 * [InformationRequestTemplateVersion].
 *
 * Only a Document requirement has one: counts, sizes, pages, issuance, coverage, and technical
 * conformance describe files, so attaching them to a typed answer or an assertion would state a rule
 * nothing could evaluate. The policy is held per binding rather than per stable requirement, because
 * a later Version may legitimately ask the same thing more or less strictly and still be asking the
 * same thing.
 *
 * Restricting which values an attribute accepts is a set of
 * [InformationRequestTemplateEvidenceAcceptedValue]. What may stand in for the Document is a set of
 * [InformationRequestTemplateBindingSubstitute]. The Version is carried alongside the binding so all
 * three freeze with the configuration that holds them.
 */
@Entity
@Table(name = "information_request_template_evidence_policy")
class InformationRequestTemplateEvidencePolicy
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_binding_id", nullable = false)
    lateinit var templateBindingId: UUID

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    /** How many files answer the requirement. An absent maximum is unbounded. */
    @Column(name = "minimum_file_count", nullable = false)
    var minimumFileCount: Int = 1

    @Column(name = "maximum_file_count", nullable = true)
    var maximumFileCount: Int? = null

    /** Per file. */
    @Column(name = "maximum_file_size_bytes", nullable = true)
    var maximumFileSizeBytes: Long? = null

    /**
     * Across the whole collection answering the requirement. Never less than [maximumFileSizeBytes],
     * which one file may already occupy on its own.
     */
    @Column(name = "maximum_total_size_bytes", nullable = true)
    var maximumTotalSizeBytes: Long? = null

    /** Per file, like [maximumFileSizeBytes]. A collection has no separate page bound. */
    @Column(name = "minimum_page_count", nullable = true)
    var minimumPageCount: Int? = null

    @Column(name = "maximum_page_count", nullable = true)
    var maximumPageCount: Int? = null

    @Column(name = "issuer_requirement", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var issuerRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED

    @Column(name = "jurisdiction_requirement", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var jurisdictionRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED

    @Column(name = "language_requirement", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var languageRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED

    @Column(name = "issue_date_requirement", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var issueDateRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED

    @Column(name = "expiry_date_requirement", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var expiryDateRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED

    @Column(name = "coverage_period_requirement", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var coveragePeriodRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED

    @Column(name = "certification_requirement", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var certificationRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED

    @Column(name = "signature_requirement", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var signatureRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED

    /**
     * How recent the issue date has to be. Admitted only while [issueDateRequirement] is
     * `REQUIRED`, because an age cannot be measured from a date that may be absent.
     */
    @Column(name = "maximum_issue_age_days", nullable = true)
    var maximumIssueAgeDays: Int? = null

    /**
     * How long the evidence still has to be valid for. Admitted only while [expiryDateRequirement]
     * is `REQUIRED`. Zero means it merely has to be unexpired.
     */
    @Column(name = "minimum_remaining_validity_days", nullable = true)
    var minimumRemainingValidityDays: Int? = null

    /** Admitted only while [coveragePeriodRequirement] is `REQUIRED`. */
    @Column(name = "minimum_coverage_days", nullable = true)
    var minimumCoverageDays: Int? = null

    /** Whether several files covering several periods have to leave no gap between them. */
    @Column(name = "coverage_continuity_required", nullable = false)
    var coverageContinuityRequired: Boolean = false

    @Column(name = "waiver_policy", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var waiverPolicy: InformationRequestEvidenceWaiverPolicy =
        InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED

    @Column(name = "conformance_policy", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var conformancePolicy: InformationRequestEvidenceConformancePolicy =
        InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED

    constructor()
}
