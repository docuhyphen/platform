import {
    InformationRequestContributorRole,
    InformationRequestEvidenceWaiverPolicy,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestSubmissionMode,
    InformationRequestTemplateRefusalDto,
    InformationRequestTemplateRequirementRequest,
} from "../../models/models.tsx";
import {TemplateDraftDocument} from "./templateDraftDocument.ts";
import {
    keyShapeMessage,
    MACHINE_KEY_PATTERN,
    requirementLabel,
    TemplateDraftProblem,
    TemplateDraftTarget,
    TemplateProblemSink,
} from "./templateDraftRules.ts";
import {conditionProblems, groupProblems, reviewProblems} from "./templatePlanValidation.ts";

export type {TemplateDraftProblem, TemplateDraftTarget} from "./templateDraftRules.ts";

const PURPOSE_PATTERN = /^[a-z0-9][a-z0-9._-]{0,127}$/;
const ANSWERABLE_MODES = new Set([InformationRequestResponseMode.PROVIDE, InformationRequestResponseMode.PROVIDE_ONCE]);

const documentProblems = (document: TemplateDraftDocument, add: TemplateProblemSink) =>
{
    if (document.sections.length === 0) add("Add at least one section.", {panel: "sections"});
    const collectsTypedData = document.sections.some(section =>
        section.requirements.some(requirement => requirement.requirementType === InformationRequestRequirementType.FIELD));
    if (collectsTypedData && !document.schemaVersionId)
    {
        add("Choose the Request Schema that typed answers are recorded against.", {panel: "settings"});
    }
    const purpose = document.factReusePurposeKey?.trim();
    if (purpose && !PURPOSE_PATTERN.test(purpose))
    {
        add(`Fact reuse purpose "${purpose}" must use lowercase letters, digits, dots, hyphens, and underscores.`,
            {panel: "settings"});
    }
};

const answerProblems = (
    requirement: InformationRequestTemplateRequirementRequest,
    document: TemplateDraftDocument,
    report: (message: string) => void,
) =>
{
    const label = requirementLabel(requirement);
    const ruleKey = requirement.conditionalRuleKey?.trim();
    if (requirement.requiredness === InformationRequestRequiredness.CONDITIONAL && !ruleKey)
    {
        report(`Requirement "${label}" applies only sometimes, so choose the condition that decides it.`);
    }
    if (ruleKey && !document.conditionRules.some(rule => rule.ruleKey === ruleKey))
    {
        report(`Requirement "${label}" names condition "${ruleKey}", which is not a condition of this Template.`);
    }
    const anchor = requirement.occurrenceAnchorKey?.trim();
    if (anchor && !document.groups.some(group => group.groupKey === anchor))
    {
        report(`Requirement "${label}" repeats per "${anchor}", which is not a group of this Template.`);
    }
    const dispositions = requirement.permittedDispositions ?? [];
    if (!ANSWERABLE_MODES.has(requirement.responseMode ?? InformationRequestResponseMode.PROVIDE))
    {
        if ((requirement.requiredness ?? InformationRequestRequiredness.OPTIONAL) !== InformationRequestRequiredness.OPTIONAL)
        {
            report(`Requirement "${label}" cannot be answered by its party, so make it optional.`);
        }
        if (dispositions.length > 0)
        {
            report(`Requirement "${label}" cannot be answered by its party, so it offers no answers to choose from.`);
        }
    }
    if (requirement.reviewPolicy === InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION
        && dispositions.every(disposition => disposition === InformationRequestResponseDisposition.PROVIDED))
    {
        report(`Requirement "${label}" is reviewed only on an exception, so it permits an answer that is one.`);
    }
};

const evidenceProblems = (requirement: InformationRequestTemplateRequirementRequest, report: (message: string) => void) =>
{
    const label = requirementLabel(requirement);
    const policy = requirement.evidencePolicy;
    if (requirement.requirementType === InformationRequestRequirementType.DOCUMENT && !policy)
    {
        report(`Requirement "${label}" needs an evidence policy to judge its files by.`);
    }
    if (!policy) return;
    if (policy.maximumFileCount !== undefined && policy.maximumFileCount < (policy.minimumFileCount ?? 1))
    {
        report(`Requirement "${label}" needs at least as many files allowed as it requires.`);
    }
    if (policy.maximumFileSizeBytes !== undefined && policy.maximumFileSizeBytes <= 0)
    {
        report(`Requirement "${label}" needs a positive file size limit.`);
    }
    if (policy.maximumPageCount !== undefined && policy.maximumPageCount < (policy.minimumPageCount ?? 0))
    {
        report(`Requirement "${label}" needs at least as many pages allowed as it requires.`);
    }
    const permitsWaiver = (requirement.permittedDispositions ?? []).includes(InformationRequestResponseDisposition.WAIVED);
    const waiver = policy.waiverPolicy ?? InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED;
    if (waiver !== InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED && !permitsWaiver)
    {
        report(`Requirement "${label}" states a waiver rule, so it permits a waived answer.`);
    }
    if (waiver === InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED && permitsWaiver)
    {
        report(`Requirement "${label}" permits a waived answer, so its evidence policy states the waiver rule that reaches it.`);
    }
};

const attestationProblems = (requirement: InformationRequestTemplateRequirementRequest, report: (message: string) => void) =>
{
    const policy = requirement.attestationPolicy;
    if (requirement.requirementType !== InformationRequestRequirementType.RESPONSE_ATTESTATION || !policy) return;
    const label = requirementLabel(requirement);
    const roles: InformationRequestContributorRole[] = policy.requiredRoles?.length
        ? policy.requiredRoles
        : [requirement.contributorRole ?? InformationRequestContributorRole.CONTRIBUTOR];
    if (new Set(roles).size !== roles.length) report(`Requirement "${label}" names the same party role twice.`);
    if (policy.minimumAssentCount !== undefined && policy.minimumAssentCount < roles.length)
    {
        report(`Requirement "${label}" needs an assent from each role it names.`);
    }
    if (policy.validityHours !== undefined && policy.validityHours < 1)
    {
        report(`Requirement "${label}" keeps an assent valid for at least one hour.`);
    }
};

const referenceProblems = (
    requirement: InformationRequestTemplateRequirementRequest,
    documentKeys: ReadonlySet<string>,
    report: (message: string) => void,
) =>
{
    const label = requirementLabel(requirement);
    [...(requirement.substituteRequirementKeys ?? []), ...(requirement.supportingEvidenceRequirementKeys ?? [])]
        .forEach(key =>
        {
            if (key === requirement.requirementKey) report(`Requirement "${label}" cannot reference itself.`);
            else if (!documentKeys.has(key))
            {
                report(`Requirement "${label}" references "${key}", which is not a requested document of this Template.`);
            }
        });
};

const sectionProblems = (
    document: TemplateDraftDocument,
    collectableFieldIds: ReadonlySet<string> | undefined,
    add: TemplateProblemSink,
) =>
{
    const staged = document.submissionMode === InformationRequestSubmissionMode.STAGED;
    const sectionKeys = new Set<string>();
    const requirementKeys = new Set<string>();
    const collectorByField = new Map<string, string>();
    const documentKeys = new Set(document.sections.flatMap(section => section.requirements
        .filter(requirement => requirement.requirementType === InformationRequestRequirementType.DOCUMENT)
        .map(requirement => requirement.requirementKey)));
    document.sections.forEach((section, sectionIndex) =>
    {
        const target = {panel: "sections" as const, sectionIndex};
        const label = section.title.trim() || section.sectionKey;
        if (!MACHINE_KEY_PATTERN.test(section.sectionKey)) add(keyShapeMessage("Section key", section.sectionKey), target);
        else if (sectionKeys.has(section.sectionKey)) add(`Two sections use the key "${section.sectionKey}".`, target);
        sectionKeys.add(section.sectionKey);
        if (!section.title.trim()) add(`Section "${label}" needs a title.`, target);
        if (section.requirements.length === 0) add(`Section "${label}" asks for nothing yet.`, target);
        const stage = section.submissionStageKey?.trim();
        if (staged && !stage) add(`Section "${label}" needs a submission stage because this Template is submitted in stages.`, target);
        if (staged && stage && !MACHINE_KEY_PATTERN.test(stage)) add(keyShapeMessage(`Section "${label}" submission stage`, stage), target);
        section.requirements.forEach((requirement, requirementIndex) =>
        {
            const report = (message: string) => add(message, {panel: "sections", sectionIndex, requirementIndex});
            const key = requirement.requirementKey;
            if (!MACHINE_KEY_PATTERN.test(key)) report(keyShapeMessage("Requirement key", key));
            else if (requirementKeys.has(key)) report(`Two requirements use the key "${key}".`);
            requirementKeys.add(key);
            if (!requirement.prompt.trim()) report(`Requirement "${key}" needs a prompt.`);
            if (requirement.requirementType === InformationRequestRequirementType.FIELD)
            {
                const fieldId = requirement.collectedFieldDefinitionId;
                if (!fieldId) report(`Requirement "${requirementLabel(requirement)}" needs the Field it collects.`);
                else if (collectableFieldIds && !collectableFieldIds.has(fieldId))
                {
                    report(`Requirement "${requirementLabel(requirement)}" collects a Field the chosen Request Schema does not ask for.`);
                }
                else if (collectorByField.has(fieldId))
                {
                    report(`Requirements "${collectorByField.get(fieldId)}" and "${requirementLabel(requirement)}" collect the same Field.`);
                }
                if (fieldId && !collectorByField.has(fieldId)) collectorByField.set(fieldId, requirementLabel(requirement));
            }
            answerProblems(requirement, document, report);
            evidenceProblems(requirement, report);
            attestationProblems(requirement, report);
            referenceProblems(requirement, documentKeys, report);
        });
    });
};

export const templateDraftProblems = (
    document: TemplateDraftDocument,
    collectableFieldIds?: ReadonlySet<string>,
): TemplateDraftProblem[] =>
{
    const problems: TemplateDraftProblem[] = [];
    const add: TemplateProblemSink = (message, target) =>
        problems.push({key: `problem-${problems.length}`, message, target});
    documentProblems(document, add);
    sectionProblems(document, collectableFieldIds, add);
    groupProblems(document, add);
    conditionProblems(document, add);
    reviewProblems(document, add);
    return problems;
};

export const targetForRefusal = (
    document: TemplateDraftDocument,
    refusal: InformationRequestTemplateRefusalDto,
): TemplateDraftTarget =>
{
    const sectionIndex = document.sections.findIndex(section =>
        refusal.requirementKey
            ? section.requirements.some(requirement => requirement.requirementKey === refusal.requirementKey)
            : section.sectionKey === refusal.sectionKey);
    if ((refusal.requirementKey || refusal.sectionKey) && sectionIndex >= 0)
    {
        const requirementIndex = document.sections[sectionIndex].requirements
            .findIndex(requirement => requirement.requirementKey === refusal.requirementKey);
        return requirementIndex >= 0
            ? {panel: "sections", sectionIndex, requirementIndex}
            : {panel: "sections", sectionIndex};
    }
    const groupIndex = document.groups.findIndex(group => group.groupKey === refusal.groupKey);
    if (refusal.groupKey && groupIndex >= 0) return {panel: "groups", groupIndex};
    const ruleIndex = document.conditionRules.findIndex(rule => rule.ruleKey === refusal.groupKey);
    if (refusal.groupKey && ruleIndex >= 0) return {panel: "conditions", ruleIndex};
    const stageIndex = document.reviewStages.findIndex(stage => stage.stageKey === refusal.reviewStageKey);
    if (refusal.reviewStageKey) return stageIndex >= 0 ? {panel: "review", stageIndex} : {panel: "review"};
    return {panel: "settings"};
};
