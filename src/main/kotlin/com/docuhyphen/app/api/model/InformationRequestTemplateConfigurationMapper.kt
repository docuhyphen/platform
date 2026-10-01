package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*

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
            reviewStageOrdering = version.reviewStageOrdering,
            reviewStages = version.reviewStages.map(::toRequest),
            factReusePurposeKey = version.factReusePurposeKey,
        )

    private fun toRequest(stage: InformationRequestTemplateReviewStageDto) =
        InformationRequestTemplateReviewStageRequest(
            stageKey = stage.stageKey,
            title = stage.title,
            aggregation = stage.aggregation,
            quorumCount = stage.quorumCount,
            minimumReviewerCount = stage.minimumReviewerCount,
            tieResolution = stage.tieResolution,
            overridePermitted = stage.overridePermitted,
            excludesResponseParties = stage.excludesResponseParties,
            excludesPriorReviewers = stage.excludesPriorReviewers,
            sectionKeys = stage.sectionKeys,
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
