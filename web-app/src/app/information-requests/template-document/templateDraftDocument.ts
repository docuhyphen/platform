import {
    InformationRequestContributorRole,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestEvidenceConformancePolicy,
    InformationRequestEvidenceWaiverPolicy,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestReviewStageOrdering,
    InformationRequestSubmissionMode,
    InformationRequestSubmissionStageOrdering,
    InformationRequestTemplateAttestationPolicyDto,
    InformationRequestTemplateAttestationPolicyRequest,
    InformationRequestTemplateConditionRuleDto,
    InformationRequestTemplateConditionRuleRequest,
    InformationRequestTemplateConfigurationRequest,
    InformationRequestTemplateEvidencePolicyDto,
    InformationRequestTemplateEvidencePolicyRequest,
    InformationRequestTemplateGroupRequest,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateRequirementRequest,
    InformationRequestTemplateReviewStageDto,
    InformationRequestTemplateReviewStageRequest,
    InformationRequestTemplateSectionRequest,
    InformationRequestTemplateVersionDto,
} from "../../models/models.tsx";

export interface TemplateDraftDocument
{
    schemaVersionId?: string;
    sections: InformationRequestTemplateSectionRequest[];
    groups: InformationRequestTemplateGroupRequest[];
    conditionRules: InformationRequestTemplateConditionRuleRequest[];
    submissionMode: InformationRequestSubmissionMode;
    submissionStageOrdering: InformationRequestSubmissionStageOrdering;
    reviewStageOrdering: InformationRequestReviewStageOrdering;
    reviewStages: InformationRequestTemplateReviewStageRequest[];
    factReusePurposeKey?: string;
}

export const emptyDraftDocument = (): TemplateDraftDocument => ({
    sections: [],
    groups: [],
    conditionRules: [],
    submissionMode: InformationRequestSubmissionMode.WHOLE_PACKAGE,
    submissionStageOrdering: InformationRequestSubmissionStageOrdering.ANY_ORDER,
    reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
    reviewStages: [],
});

const withoutId = <T extends {id: string}>(value: T): Omit<T, "id"> =>
{
    const copy: Partial<T> = {...value};
    delete copy.id;
    return copy as Omit<T, "id">;
};

const evidencePolicyRequest = (
    policy: InformationRequestTemplateEvidencePolicyDto,
): InformationRequestTemplateEvidencePolicyRequest => ({
    ...withoutId(policy),
    acceptedValues: policy.acceptedValues.map(value => ({...value})),
});

const attestationPolicyRequest = (
    policy: InformationRequestTemplateAttestationPolicyDto,
): InformationRequestTemplateAttestationPolicyRequest => ({
    ...withoutId(policy),
    requiredRoles: [...policy.requiredRoles],
});

const requirementRequest = (requirement: InformationRequestTemplateRequirementDto): InformationRequestTemplateRequirementRequest =>
{
    const statement: Partial<InformationRequestTemplateRequirementDto> = {...requirement};
    delete statement.id;
    delete statement.templateRequirementId;
    delete statement.evidencePolicy;
    delete statement.attestationPolicy;
    return {
        ...(statement as Omit<InformationRequestTemplateRequirementDto,
            "id" | "templateRequirementId" | "evidencePolicy" | "attestationPolicy">),
        permittedDispositions: [...requirement.permittedDispositions],
        substituteRequirementKeys: [...requirement.substituteRequirementKeys],
        supportingEvidenceRequirementKeys: [...requirement.supportingEvidenceRequirementKeys],
        ...(requirement.evidencePolicy ? {evidencePolicy: evidencePolicyRequest(requirement.evidencePolicy)} : {}),
        ...(requirement.attestationPolicy
            ? {attestationPolicy: attestationPolicyRequest(requirement.attestationPolicy)}
            : {}),
    };
};

const conditionRuleRequest = (rule: InformationRequestTemplateConditionRuleDto): InformationRequestTemplateConditionRuleRequest => ({
    ruleKey: rule.ruleKey,
    expressionVersion: rule.expressionVersion,
    hiddenDataPolicy: rule.hiddenDataPolicy,
    predicates: rule.predicates.map(withoutId),
});

const reviewStageRequest = (stage: InformationRequestTemplateReviewStageDto): InformationRequestTemplateReviewStageRequest => ({
    ...withoutId(stage),
    sectionKeys: [...stage.sectionKeys],
});

export const draftDocumentFromVersion = (version?: InformationRequestTemplateVersionDto): TemplateDraftDocument =>
{
    if (!version) return emptyDraftDocument();
    return {
        ...(version.schemaVersionId ? {schemaVersionId: version.schemaVersionId} : {}),
        sections: version.sections.map(section => ({
            sectionKey: section.sectionKey,
            title: section.title,
            ...(section.helpText ? {helpText: section.helpText} : {}),
            ...(section.submissionStageKey ? {submissionStageKey: section.submissionStageKey} : {}),
            requirements: section.requirements.map(requirementRequest),
        })),
        groups: version.groups.map(withoutId),
        conditionRules: version.conditionRules.map(conditionRuleRequest),
        submissionMode: version.submissionMode ?? InformationRequestSubmissionMode.WHOLE_PACKAGE,
        submissionStageOrdering: version.submissionStageOrdering ?? InformationRequestSubmissionStageOrdering.ANY_ORDER,
        reviewStageOrdering: version.reviewStageOrdering ?? InformationRequestReviewStageOrdering.SEQUENTIAL,
        reviewStages: (version.reviewStages ?? []).map(reviewStageRequest),
        ...(version.factReusePurposeKey ? {factReusePurposeKey: version.factReusePurposeKey} : {}),
    };
};

export const configurationRequestFrom = (document: TemplateDraftDocument): InformationRequestTemplateConfigurationRequest =>
{
    const staged = document.submissionMode === InformationRequestSubmissionMode.STAGED;
    const purpose = document.factReusePurposeKey?.trim();
    return {
        schemaVersionId: document.schemaVersionId || undefined,
        sections: document.sections.map(section => ({
            ...section,
            submissionStageKey: staged ? section.submissionStageKey : undefined,
        })),
        groups: document.groups,
        conditionRules: document.conditionRules,
        submissionMode: document.submissionMode,
        submissionStageOrdering: staged
            ? document.submissionStageOrdering
            : InformationRequestSubmissionStageOrdering.ANY_ORDER,
        reviewStageOrdering: document.reviewStageOrdering,
        reviewStages: document.reviewStages,
        factReusePurposeKey: purpose ? purpose : undefined,
    };
};

export const defaultEvidencePolicy = (): InformationRequestTemplateEvidencePolicyRequest => ({
    minimumFileCount: 1,
    issuerRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    jurisdictionRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    languageRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    issueDateRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    expiryDateRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    coveragePeriodRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    certificationRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    signatureRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    coverageContinuityRequired: false,
    waiverPolicy: InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED,
    conformancePolicy: InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED,
    acceptedValues: [],
});

export const newRequirement = (
    requirementType: InformationRequestRequirementType,
    requirementKey: string,
    prompt: string,
): InformationRequestTemplateRequirementRequest => ({
    requirementKey,
    requirementType,
    prompt,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
    ...(requirementType === InformationRequestRequirementType.DOCUMENT ? {evidencePolicy: defaultEvidencePolicy()} : {}),
    ...(requirementType === InformationRequestRequirementType.RESPONSE_ATTESTATION
        ? {attestationPolicy: {requiredRoles: [InformationRequestContributorRole.CONTRIBUTOR]}}
        : {}),
});

export const moveItem = <T>(items: T[], index: number, offset: number): T[] =>
{
    const target = index + offset;
    if (index < 0 || index >= items.length || target < 0 || target >= items.length) return items;
    const moved = [...items];
    const [item] = moved.splice(index, 1);
    moved.splice(target, 0, item);
    return moved;
};

export const replaceItem = <T>(items: T[], index: number, item: T): T[] =>
    items.map((existing, position) => position === index ? item : existing);

export const removeItem = <T>(items: T[], index: number): T[] =>
    items.filter((_, position) => position !== index);

export const keyFromLabel = (label: string): string =>
    label.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
