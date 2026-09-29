import {
    InformationRequestContributorRole,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestTemplateRequirementRequest,
} from "../../../models/models.tsx";
import {TemplateDraftDocument, defaultEvidencePolicy} from "../templateDraftDocument.ts";

export type RequirementDraft = InformationRequestTemplateRequirementRequest;

export interface RequirementFieldsProps
{
    id: string;
    draft: RequirementDraft;
    readOnly: boolean;
    onChange: (change: Partial<RequirementDraft>) => void;
}

const ANSWERABLE = new Set([InformationRequestResponseMode.PROVIDE, InformationRequestResponseMode.PROVIDE_ONCE]);

export const withRequirementType = (draft: RequirementDraft, requirementType: InformationRequestRequirementType): RequirementDraft => ({
    ...draft,
    requirementType,
    collectedFieldDefinitionId: requirementType === InformationRequestRequirementType.FIELD
        ? draft.collectedFieldDefinitionId
        : undefined,
    evidencePolicy: requirementType === InformationRequestRequirementType.DOCUMENT
        ? draft.evidencePolicy ?? defaultEvidencePolicy()
        : undefined,
    substituteRequirementKeys: requirementType === InformationRequestRequirementType.DOCUMENT
        ? draft.substituteRequirementKeys
        : [],
    attestationPolicy: requirementType === InformationRequestRequirementType.RESPONSE_ATTESTATION
        ? draft.attestationPolicy ?? {requiredRoles: [draft.contributorRole ?? InformationRequestContributorRole.CONTRIBUTOR]}
        : undefined,
});

export const withResponseMode = (draft: RequirementDraft, responseMode: InformationRequestResponseMode): RequirementDraft =>
{
    if (ANSWERABLE.has(responseMode))
    {
        const dispositions = draft.permittedDispositions ?? [];
        return {
            ...draft,
            responseMode,
            permittedDispositions: dispositions.length > 0 ? dispositions : [InformationRequestResponseDisposition.PROVIDED],
        };
    }
    return {
        ...draft,
        responseMode,
        requiredness: InformationRequestRequiredness.OPTIONAL,
        permittedDispositions: [],
        attestationPolicy: undefined,
    };
};

export const isAnswerable = (draft: RequirementDraft): boolean =>
    ANSWERABLE.has(draft.responseMode ?? InformationRequestResponseMode.PROVIDE);

export const withRequirementSaved = (
    document: TemplateDraftDocument,
    sectionIndex: number,
    requirementIndex: number | undefined,
    saved: RequirementDraft,
): TemplateDraftDocument =>
{
    const previousKey = requirementIndex === undefined
        ? undefined
        : document.sections[sectionIndex].requirements[requirementIndex].requirementKey;
    const rename = (key: string) => previousKey !== undefined && key === previousKey ? saved.requirementKey : key;
    const sections = document.sections.map((section, index) =>
    {
        const requirements = section.requirements.map(requirement => ({
            ...requirement,
            substituteRequirementKeys: (requirement.substituteRequirementKeys ?? []).map(rename),
            supportingEvidenceRequirementKeys: (requirement.supportingEvidenceRequirementKeys ?? []).map(rename),
        }));
        if (index !== sectionIndex) return {...section, requirements};
        return {
            ...section,
            requirements: requirementIndex === undefined
                ? [...requirements, saved]
                : requirements.map((requirement, position) => position === requirementIndex ? saved : requirement),
        };
    });
    return {
        ...document,
        sections,
        conditionRules: document.conditionRules.map(rule => ({
            ...rule,
            predicates: rule.predicates.map(predicate => predicate.sourceRequirementKey
                ? {...predicate, sourceRequirementKey: rename(predicate.sourceRequirementKey)}
                : predicate),
        })),
    };
};
