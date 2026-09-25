package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidencePolicy
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCapturedAttribute
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidencePolicy
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidenceAcceptedValueRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidencePolicyRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestEvidencePolicyLoader @Inject constructor(
    private val policyRepository: InformationRequestTemplateEvidencePolicyRepository,
    private val acceptedValueRepository: InformationRequestTemplateEvidenceAcceptedValueRepository,
)
{
    fun forBinding(bindingId: UUID): InformationRequestEvidencePolicy? =
        policyRepository.findForBinding(bindingId)?.let(::toPolicy)

    private fun toPolicy(policy: InformationRequestTemplateEvidencePolicy): InformationRequestEvidencePolicy =
        InformationRequestEvidencePolicy(
            minimumFileCount = policy.minimumFileCount,
            maximumFileCount = policy.maximumFileCount,
            maximumFileSizeBytes = policy.maximumFileSizeBytes,
            maximumTotalSizeBytes = policy.maximumTotalSizeBytes,
            minimumPageCount = policy.minimumPageCount,
            maximumPageCount = policy.maximumPageCount,
            attributeRequirements = mapOf(
                InformationRequestEvidenceCapturedAttribute.ISSUER to policy.issuerRequirement,
                InformationRequestEvidenceCapturedAttribute.JURISDICTION to policy.jurisdictionRequirement,
                InformationRequestEvidenceCapturedAttribute.LANGUAGE to policy.languageRequirement,
                InformationRequestEvidenceCapturedAttribute.ISSUE_DATE to policy.issueDateRequirement,
                InformationRequestEvidenceCapturedAttribute.EXPIRY_DATE to policy.expiryDateRequirement,
                InformationRequestEvidenceCapturedAttribute.COVERAGE_PERIOD to policy.coveragePeriodRequirement,
                InformationRequestEvidenceCapturedAttribute.CERTIFICATION to policy.certificationRequirement,
                InformationRequestEvidenceCapturedAttribute.SIGNATURE to policy.signatureRequirement,
            ),
            acceptedValues = acceptedValueRepository.findForPolicy(policy.id)
                .groupBy({ it.attribute }, { it.acceptedValue })
                .mapValues { (_, values) -> values.toSet() },
            maximumIssueAgeDays = policy.maximumIssueAgeDays,
            minimumRemainingValidityDays = policy.minimumRemainingValidityDays,
            minimumCoverageDays = policy.minimumCoverageDays,
            coverageContinuityRequired = policy.coverageContinuityRequired,
            waiverPolicy = policy.waiverPolicy,
            conformancePolicy = policy.conformancePolicy,
        )
}
