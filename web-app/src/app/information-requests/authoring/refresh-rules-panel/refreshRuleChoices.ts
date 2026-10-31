import {
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestRefreshRuleDto,
    InformationRequestRequirementType,
    InformationRequestTemplateSectionDto,
} from "../../../models/models.tsx";

export interface RefreshableDocument {
    requirementKey: string;
    label: string;
}

export const refreshableDocuments = (sections: InformationRequestTemplateSectionDto[]): RefreshableDocument[] => {
    const seen = new Set<string>();
    return sections
        .flatMap(section => section.requirements)
        .filter(requirement => requirement.requirementType === InformationRequestRequirementType.DOCUMENT)
        .filter(requirement => (requirement.evidencePolicy?.expiryDateRequirement ?? InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED)
            !== InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED)
        .filter(requirement => {
            if (seen.has(requirement.requirementKey)) return false;
            seen.add(requirement.requirementKey);
            return true;
        })
        .map(requirement => ({
            requirementKey: requirement.requirementKey,
            label: requirement.prompt.trim() || requirement.requirementKey,
        }));
};

export const leadDaysSentence = (leadDays: number): string =>
    `A new request is created ${leadDays} ${leadDays === 1 ? "day" : "days"} before its evidence expires.`;

export const refreshStateSentence = (rule: InformationRequestRefreshRuleDto): string => {
    if (rule.refreshed) return "Refreshed for the latest submission.";
    if (rule.refreshDue) return "Due for refresh now.";
    return rule.refreshDueOn
        ? `Due for refresh from ${rule.refreshDueOn}.`
        : "No submitted file of this document has an expiry date yet.";
};
