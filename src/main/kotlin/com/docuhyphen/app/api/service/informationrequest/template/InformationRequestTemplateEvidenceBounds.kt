package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttributeRequirement

internal object InformationRequestTemplateEvidenceBounds {
    fun problemOf(policy: InformationRequestTemplateEvidencePolicyRequest): String? =
        fileProblemOf(policy) ?: pageProblemOf(policy) ?: dateProblemOf(policy)

    private fun fileProblemOf(policy: InformationRequestTemplateEvidencePolicyRequest): String? {
        val maximumFiles = policy.maximumFileCount
        val maximumFileSize = policy.maximumFileSizeBytes
        val maximumTotalSize = policy.maximumTotalSizeBytes
        return when {
            policy.minimumFileCount < 1 -> "needs at least one file"
            maximumFiles != null && maximumFiles < policy.minimumFileCount ->
                "allows fewer files than it requires"

            maximumFileSize != null && maximumFileSize <= 0 -> "needs a positive file size limit"
            maximumTotalSize != null && maximumTotalSize <= 0 -> "needs a positive total size limit"
            maximumTotalSize != null && maximumFileSize != null && maximumTotalSize < maximumFileSize ->
                "allows less in total than one file may hold"

            else -> null
        }
    }

    private fun pageProblemOf(policy: InformationRequestTemplateEvidencePolicyRequest): String? {
        val minimumPages = policy.minimumPageCount
        val maximumPages = policy.maximumPageCount
        return when {
            minimumPages != null && minimumPages < 1 -> "needs a minimum of at least one page"
            maximumPages != null && maximumPages < 1 -> "needs a maximum of at least one page"
            minimumPages != null && maximumPages != null && maximumPages < minimumPages ->
                "allows fewer pages than it requires"

            else -> null
        }
    }

    private fun dateProblemOf(policy: InformationRequestTemplateEvidencePolicyRequest): String? {
        val maximumAge = policy.maximumIssueAgeDays
        val minimumValidity = policy.minimumRemainingValidityDays
        val minimumCoverage = policy.minimumCoverageDays
        val boundsCoverage = minimumCoverage != null || policy.coverageContinuityRequired
        return when {
            maximumAge != null && maximumAge < 1 -> "needs a maximum age since issue of at least one day"
            maximumAge != null && policy.issueDateRequirement != REQUIRED ->
                "limits the age since issue, so it requires each file's issue date"

            minimumValidity != null && minimumValidity < 0 -> "needs a minimum validity remaining of zero days or more"
            minimumValidity != null && policy.expiryDateRequirement != REQUIRED ->
                "asks for validity remaining, so it requires each file's expiry date"

            minimumCoverage != null && minimumCoverage < 1 -> "needs a minimum coverage of at least one day"
            boundsCoverage && policy.coveragePeriodRequirement != REQUIRED ->
                "sets coverage rules, so it requires each file's coverage period"

            else -> null
        }
    }

    private val REQUIRED = InformationRequestEvidenceAttributeRequirement.REQUIRED
}
