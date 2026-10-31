package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.util.*

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

    @Column(name = "minimum_file_count", nullable = false)
    var minimumFileCount: Int = 1

    @Column(name = "maximum_file_count", nullable = true)
    var maximumFileCount: Int? = null

    @Column(name = "maximum_file_size_bytes", nullable = true)
    var maximumFileSizeBytes: Long? = null

    @Column(name = "maximum_total_size_bytes", nullable = true)
    var maximumTotalSizeBytes: Long? = null

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

    @Column(name = "maximum_issue_age_days", nullable = true)
    var maximumIssueAgeDays: Int? = null

    @Column(name = "minimum_remaining_validity_days", nullable = true)
    var minimumRemainingValidityDays: Int? = null

    @Column(name = "minimum_coverage_days", nullable = true)
    var minimumCoverageDays: Int? = null

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
