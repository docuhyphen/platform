package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAcceptedValueRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAttestationPolicyDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAttestationPolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto

/** Reconstructs an editable authored document from one frozen Template Version projection. */
object InformationRequestTemplateConfigurationMapper
{
    fun toRequest(version: InformationRequestTemplateVersionDto) =
        InformationRequestTemplateConfigurationRequest(
            schemaVersionId = version.schemaVersionId,
            sections = version.sections.map { section ->
                InformationRequestTemplateSectionRequest(
                    sectionKey = section.sectionKey,
                    title = section.title,
                    helpText = section.helpText,
                    requirements = section.requirements.map(::toRequest),
                    submissionStageKey = section.submissionStageKey,
                )
            },
            groups = version.groups.map(::toRequest),
            conditionRules = version.conditionRules.map(::toRequest),
            submissionMode = version.submissionMode,
            submissionStageOrdering = version.submissionStageOrdering,
        )

    private fun toRequest(rule: InformationRequestTemplateConditionRuleDto) =
        InformationRequestTemplateConditionRuleRequest(
            ruleKey = rule.ruleKey,
            expressionVersion = rule.expressionVersion,
            hiddenDataPolicy = rule.hiddenDataPolicy,
            predicates = rule.predicates.map(::toRequest),
        )

    private fun toRequest(predicate: InformationRequestTemplateConditionPredicateDto) =
        InformationRequestTemplateConditionPredicateRequest(
            sourceRequirementKey = predicate.sourceRequirementKey,
            fieldDefinitionId = predicate.fieldDefinitionId,
            valueType = predicate.valueType,
            operator = predicate.operator,
            value = predicate.value,
            expectedDisposition = predicate.expectedDisposition,
        )

    private fun toRequest(group: InformationRequestTemplateGroupDto) =
        InformationRequestTemplateGroupRequest(
            groupKey = group.groupKey,
            parentGroupKey = group.parentGroupKey,
            minOccurrences = group.minOccurrences,
            maxOccurrences = group.maxOccurrences,
        )

    private fun toRequest(requirement: InformationRequestTemplateRequirementDto) =
        InformationRequestTemplateRequirementRequest(
            requirementKey = requirement.requirementKey,
            requirementType = requirement.requirementType,
            prompt = requirement.prompt,
            helpText = requirement.helpText,
            responseMode = requirement.responseMode,
            requiredness = requirement.requiredness,
            contributorRole = requirement.contributorRole,
            reviewPolicy = requirement.reviewPolicy,
            confidentialityCompartmentKey = requirement.confidentialityCompartmentKey,
            conditionalRuleKey = requirement.conditionalRuleKey,
            occurrenceAnchorKey = requirement.occurrenceAnchorKey,
            collectedFieldDefinitionId = requirement.collectedFieldDefinitionId,
            permittedDispositions = requirement.permittedDispositions,
            evidencePolicy = requirement.evidencePolicy?.let(::toRequest),
            substituteRequirementKeys = requirement.substituteRequirementKeys,
            supportingEvidenceRequirementKeys = requirement.supportingEvidenceRequirementKeys,
            attestationPolicy = requirement.attestationPolicy?.let(::toRequest),
        )

    private fun toRequest(policy: InformationRequestTemplateAttestationPolicyDto) =
        InformationRequestTemplateAttestationPolicyRequest(
            requiredRoles = policy.requiredRoles,
            ordering = policy.ordering,
            minimumAssentCount = policy.minimumAssentCount,
            minimumAuthenticationStrength = policy.minimumAuthenticationStrength,
            validityHours = policy.validityHours,
            externalSignatureReference = policy.externalSignatureReference,
        )

    private fun toRequest(policy: InformationRequestTemplateEvidencePolicyDto) =
        InformationRequestTemplateEvidencePolicyRequest(
            minimumFileCount = policy.minimumFileCount,
            maximumFileCount = policy.maximumFileCount,
            maximumFileSizeBytes = policy.maximumFileSizeBytes,
            maximumTotalSizeBytes = policy.maximumTotalSizeBytes,
            minimumPageCount = policy.minimumPageCount,
            maximumPageCount = policy.maximumPageCount,
            issuerRequirement = policy.issuerRequirement,
            jurisdictionRequirement = policy.jurisdictionRequirement,
            languageRequirement = policy.languageRequirement,
            issueDateRequirement = policy.issueDateRequirement,
            expiryDateRequirement = policy.expiryDateRequirement,
            coveragePeriodRequirement = policy.coveragePeriodRequirement,
            certificationRequirement = policy.certificationRequirement,
            signatureRequirement = policy.signatureRequirement,
            maximumIssueAgeDays = policy.maximumIssueAgeDays,
            minimumRemainingValidityDays = policy.minimumRemainingValidityDays,
            minimumCoverageDays = policy.minimumCoverageDays,
            coverageContinuityRequired = policy.coverageContinuityRequired,
            waiverPolicy = policy.waiverPolicy,
            conformancePolicy = policy.conformancePolicy,
            acceptedValues = policy.acceptedValues.map { accepted ->
                InformationRequestTemplateAcceptedValueRequest(
                    attribute = accepted.attribute,
                    acceptedValue = accepted.acceptedValue,
                )
            },
        )
}
