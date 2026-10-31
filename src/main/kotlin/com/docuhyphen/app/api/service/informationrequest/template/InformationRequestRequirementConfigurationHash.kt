package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateBindingConfiguration
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object InformationRequestRequirementConfigurationHash {
    fun of(
        binding: InformationRequestTemplateRequirementBinding,
        requirement: InformationRequestTemplateRequirement,
        configuration: InformationRequestTemplateBindingConfiguration,
    ): String {
        val policy = configuration.policiesByBinding[binding.id]
        val material = listOf(
            "binding:${binding.id}",
            "requirement:${requirement.id}",
            "requirementKey:${requirement.requirementKey}",
            "requirementType:${requirement.requirementType}",
            "prompt:${binding.prompt}",
            "helpText:${binding.helpText.orEmpty()}",
            "responseMode:${binding.responseMode}",
            "requiredness:${binding.requiredness}",
            "contributorRole:${binding.contributorRole}",
            "reviewPolicy:${binding.reviewPolicy}",
            "confidentiality:${binding.confidentialityCompartmentKey.orEmpty()}",
            "condition:${binding.conditionalRuleKey.orEmpty()}",
            "occurrence:${binding.occurrenceAnchorKey.orEmpty()}",
            "collectedField:${binding.collectedFieldDefinitionId?.toString().orEmpty()}",
            "dispositions:${configuration.dispositionsByBinding[binding.id].orEmpty().map { it.disposition.name }}",
            "policy:${policy?.stableHashMaterial().orEmpty()}",
            "accepted:${policy?.let { acceptedHashMaterial(it, configuration) }.orEmpty()}",
            "substitutes:${
                configuration.substitutesByBinding[binding.id].orEmpty().map { it.substituteTemplateBindingId }
            }",
            "supporting:${
                configuration.evidenceLinksByBinding[binding.id].orEmpty().map { it.supportingTemplateBindingId }
            }",
        ).joinToString(separator = "\n")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun acceptedHashMaterial(
        policy: InformationRequestTemplateEvidencePolicy,
        configuration: InformationRequestTemplateBindingConfiguration,
    ): List<String> = configuration.acceptedValuesByPolicy[policy.id]
        .orEmpty()
        .map { "${it.attribute}:${it.acceptedValue}" }

    private fun InformationRequestTemplateEvidencePolicy.stableHashMaterial(): String = listOf(
        minimumFileCount,
        maximumFileCount,
        maximumFileSizeBytes,
        maximumTotalSizeBytes,
        minimumPageCount,
        maximumPageCount,
        issuerRequirement,
        jurisdictionRequirement,
        languageRequirement,
        issueDateRequirement,
        expiryDateRequirement,
        coveragePeriodRequirement,
        certificationRequirement,
        signatureRequirement,
        maximumIssueAgeDays,
        minimumRemainingValidityDays,
        minimumCoverageDays,
        coverageContinuityRequired,
        waiverPolicy,
        conformancePolicy,
    ).joinToString(separator = "|")
}
