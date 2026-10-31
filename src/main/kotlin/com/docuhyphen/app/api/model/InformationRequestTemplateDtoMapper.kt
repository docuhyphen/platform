package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionPredicateLiteralCodec

object InformationRequestTemplateDtoMapper
{
    fun toDto(
        definition: InformationRequestTemplateDefinition,
        draftVersion: InformationRequestTemplateVersionDto?,
        latestPublishedVersion: InformationRequestTemplateVersionDto?,
        draftETag: String? = null,
        versions: List<InformationRequestTemplateVersion> = emptyList(),
    ): InformationRequestTemplateDto = InformationRequestTemplateDto(
        id = definition.id,
        scopeKind = definition.scopeKind,
        scopeOrgId = definition.scopeOrgId,
        scopeUserId = definition.scopeUserId,
        namespace = definition.namespace,
        templateKey = definition.templateKey,
        displayName = definition.displayName,
        description = definition.description,
        status = definition.status,
        draftVersion = draftVersion,
        draftETag = draftETag,
        latestPublishedVersion = latestPublishedVersion,
        createdAt = definition.createdAt,
        updatedAt = definition.updatedAt,
        versions = versions
            .filter { it.status != InformationRequestTemplateStatus.DRAFT }
            .sortedBy { it.versionNumber }
            .map {
                InformationRequestTemplateVersionStandingDto(
                    it.versionNumber,
                    it.status,
                    it.publishedAt,
                    it.retiredAt
                )
            },
    )

    fun toSummaryDto(
        definition: InformationRequestTemplateDefinition,
        versions: List<InformationRequestTemplateVersion>,
    ): InformationRequestTemplateSummaryDto {
        val latestPublished = versions
            .filter { it.status == InformationRequestTemplateStatus.PUBLISHED }
            .maxByOrNull { it.versionNumber }
        return InformationRequestTemplateSummaryDto(
            id = definition.id,
            scopeKind = definition.scopeKind,
            scopeOrgId = definition.scopeOrgId,
            scopeUserId = definition.scopeUserId,
            namespace = definition.namespace,
            templateKey = definition.templateKey,
            displayName = definition.displayName,
            description = definition.description,
            status = definition.status,
            hasEditableVersion = versions.any { it.status == InformationRequestTemplateStatus.DRAFT },
            latestPublishedVersionNumber = latestPublished?.versionNumber,
            latestPublishedVersionId = latestPublished?.id,
            createdAt = definition.createdAt,
            updatedAt = definition.updatedAt,
        )
    }

    fun toDto(
        version: InformationRequestTemplateVersion,
        sections: List<InformationRequestTemplateSectionDto>,
        groups: List<InformationRequestTemplateGroupDto>,
        conditionRules: List<InformationRequestTemplateConditionRuleDto>,
        requiredCapabilities: List<InformationRequestTemplateCapabilityDto>,
        reviewStages: List<InformationRequestTemplateReviewStageDto> = emptyList(),
    ): InformationRequestTemplateVersionDto = InformationRequestTemplateVersionDto(
        id = version.id,
        templateDefinitionId = version.templateDefinitionId,
        versionNumber = version.versionNumber,
        status = version.status,
        schemaVersionId = version.schemaVersionId,
        submissionMode = version.submissionMode,
        submissionStageOrdering = version.submissionStageOrdering,
        reviewStageOrdering = version.reviewStageOrdering,
        reviewStages = reviewStages,
        factReusePurposeKey = version.factReusePurposeKey,
        sections = sections,
        groups = groups,
        conditionRules = conditionRules,
        requiredCapabilities = requiredCapabilities,
        publishedAt = version.publishedAt,
        retiredAt = version.retiredAt,
        createdAt = version.createdAt,
    )

    fun toDto(
        stage: InformationRequestTemplateReviewStage,
        sectionKeys: List<String>,
    ): InformationRequestTemplateReviewStageDto = InformationRequestTemplateReviewStageDto(
        id = stage.id,
        stageKey = stage.stageKey,
        title = stage.title,
        aggregation = stage.aggregation,
        quorumCount = stage.quorumCount,
        minimumReviewerCount = stage.minimumReviewerCount,
        tieResolution = stage.tieResolution,
        overridePermitted = stage.overridePermitted,
        excludesResponseParties = stage.excludesResponseParties,
        excludesPriorReviewers = stage.excludesPriorReviewers,
        sectionKeys = sectionKeys,
    )

    fun toDto(
        rule: InformationRequestTemplateConditionRule,
        predicates: List<InformationRequestTemplateConditionPredicateDto>,
    ): InformationRequestTemplateConditionRuleDto = InformationRequestTemplateConditionRuleDto(
        id = rule.id,
        ruleKey = rule.ruleKey,
        expressionVersion = rule.expressionVersion,
        hiddenDataPolicy = rule.hiddenDataPolicy,
        predicates = predicates,
    )

    fun toDto(
        predicate: InformationRequestTemplateConditionPredicate,
        literalValues: List<String>,
    ): InformationRequestTemplateConditionPredicateDto = InformationRequestTemplateConditionPredicateDto(
        id = predicate.id,
        sourceRequirementKey = predicate.sourceRequirementKey,
        fieldDefinitionId = predicate.fieldDefinitionId,
        valueType = predicate.valueType,
        operator = predicate.operator,
        value = InformationRequestConditionPredicateLiteralCodec.toJson(predicate, literalValues),
        expectedDisposition = predicate.expectedDisposition,
    )

    fun toEvaluationRequest(
        rule: InformationRequestTemplateConditionRuleDto,
    ): InformationRequestTemplateConditionRuleRequest = InformationRequestTemplateConfigurationMapper.toRequest(rule)

    fun toEvaluationRequest(
        predicate: InformationRequestTemplateConditionPredicateDto,
    ): InformationRequestTemplateConditionPredicateRequest =
        InformationRequestTemplateConfigurationMapper.toRequest(predicate)

    fun toDto(
        group: InformationRequestTemplateRequirementGroup,
        parentGroupKey: String?,
    ): InformationRequestTemplateGroupDto = InformationRequestTemplateGroupDto(
        id = group.id,
        groupKey = group.groupKey,
        parentGroupKey = parentGroupKey,
        minOccurrences = group.minOccurrences,
        maxOccurrences = group.maxOccurrences,
    )

    fun toDto(
        section: InformationRequestTemplateSection,
        requirements: List<InformationRequestTemplateRequirementDto>,
    ): InformationRequestTemplateSectionDto = InformationRequestTemplateSectionDto(
        id = section.id,
        sectionKey = section.sectionKey,
        title = section.title,
        helpText = section.helpText,
        submissionStageKey = section.submissionStageKey,
        requirements = requirements,
    )

    fun toDto(
        binding: InformationRequestTemplateRequirementBinding,
        requirement: InformationRequestTemplateRequirement,
        permittedDispositions: List<InformationRequestResponseDisposition>,
        evidencePolicy: InformationRequestTemplateEvidencePolicyDto?,
        substituteRequirementKeys: List<String>,
        supportingEvidenceRequirementKeys: List<String>,
        attestationPolicy: InformationRequestTemplateAttestationPolicyDto? = null,
    ): InformationRequestTemplateRequirementDto = InformationRequestTemplateRequirementDto(
        id = binding.id,
        templateRequirementId = requirement.id,
        requirementKey = requirement.requirementKey,
        requirementType = requirement.requirementType,
        prompt = binding.prompt,
        helpText = binding.helpText,
        responseMode = binding.responseMode,
        requiredness = binding.requiredness,
        contributorRole = binding.contributorRole,
        reviewPolicy = binding.reviewPolicy,
        confidentialityCompartmentKey = binding.confidentialityCompartmentKey,
        conditionalRuleKey = binding.conditionalRuleKey,
        occurrenceAnchorKey = binding.occurrenceAnchorKey,
        collectedFieldDefinitionId = binding.collectedFieldDefinitionId,
        permittedDispositions = permittedDispositions,
        evidencePolicy = evidencePolicy,
        substituteRequirementKeys = substituteRequirementKeys,
        supportingEvidenceRequirementKeys = supportingEvidenceRequirementKeys,
        attestationPolicy = attestationPolicy,
    )

    fun toDto(
        policy: InformationRequestTemplateAttestationPolicy,
        roles: List<InformationRequestTemplateAttestationRole>,
    ): InformationRequestTemplateAttestationPolicyDto = InformationRequestTemplateAttestationPolicyDto(
        id = policy.id,
        requiredRoles = roles.sortedBy { it.position }.map { it.roleKey },
        ordering = policy.ordering,
        minimumAssentCount = policy.minimumAssentCount,
        minimumAuthenticationStrength = policy.minimumAuthenticationStrength,
        validityHours = policy.validityHours,
        externalSignatureReference = policy.externalSignatureReference,
    )

    fun toDto(
        policy: InformationRequestTemplateEvidencePolicy,
        acceptedValues: List<InformationRequestTemplateEvidenceAcceptedValue>,
    ): InformationRequestTemplateEvidencePolicyDto = InformationRequestTemplateEvidencePolicyDto(
        id = policy.id,
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
        acceptedValues = acceptedValues.map(::toDto),
    )

    fun toDto(
        accepted: InformationRequestTemplateEvidenceAcceptedValue,
    ): InformationRequestTemplateAcceptedValueDto = InformationRequestTemplateAcceptedValueDto(
        attribute = accepted.attribute,
        acceptedValue = accepted.acceptedValue,
    )

    fun toDto(
        recorded: InformationRequestTemplateVersionCapability,
    ): InformationRequestTemplateCapabilityDto = InformationRequestTemplateCapabilityDto(
        capability = recorded.capabilityKey,
        requiredContractVersion = recorded.requiredContractVersion,
    )
}
