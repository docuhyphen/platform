import {
    InformationRequestRequiredness,
    InformationRequestSubmissionMode,
    InformationRequestTemplateRequirementRequest,
} from "../../models/models.tsx";
import {TemplateDraftDocument} from "./templateDraftDocument.ts";
import {requirementLabel, TemplateDraftTarget, TemplateProblemSink} from "./templateDraftRules.ts";
import {collectorsOf, sourceKeysOf} from "./templatePlanValidation.ts";

type Requirement = InformationRequestTemplateRequirementRequest;

const requirementsOf = (document: TemplateDraftDocument): Requirement[] =>
    document.sections.flatMap(section => section.requirements);

const anchorOf = (requirement: Requirement): string | undefined => requirement.occurrenceAnchorKey?.trim() || undefined;

const locate = (document: TemplateDraftDocument, requirementKey: string): TemplateDraftTarget => {
    const sectionIndex = document.sections.findIndex(section =>
        section.requirements.some(requirement => requirement.requirementKey === requirementKey));
    if (sectionIndex < 0) return {panel: "sections"};
    const requirementIndex = document.sections[sectionIndex].requirements
        .findIndex(requirement => requirement.requirementKey === requirementKey);
    return {panel: "sections", sectionIndex, requirementIndex};
};

const listed = (items: string[]): string =>
    items.length < 2 ? items.join("") : `${items.slice(0, -1).join(", ")} and ${items[items.length - 1]}`;

const conditionCycleProblems = (document: TemplateDraftDocument, add: TemplateProblemSink) => {
    const requirements = requirementsOf(document);
    const collectors = collectorsOf(requirements);
    const conditional = new Map(requirements
        .filter(requirement => requirement.requiredness === InformationRequestRequiredness.CONDITIONAL && requirement.conditionalRuleKey)
        .map(requirement => [requirement.requirementKey, requirement]));
    const dependencies = new Map([...conditional].map(([key, requirement]) => {
        const rule = document.conditionRules.find(candidate => candidate.ruleKey === requirement.conditionalRuleKey);
        return [key, rule ? sourceKeysOf(rule, collectors).filter(source => conditional.has(source)) : []];
    }));
    const checked = new Set<string>();
    const path: string[] = [];
    const visit = (key: string) => {
        if (checked.has(key)) return;
        const repeated = path.indexOf(key);
        if (repeated >= 0) {
            add(`Conditional requirements ${[...path.slice(repeated), key].join(" then ")} depend on each other.`, locate(document, key));
            return;
        }
        path.push(key);
        (dependencies.get(key) ?? []).forEach(visit);
        path.pop();
        checked.add(key);
    };
    [...dependencies.keys()].forEach(visit);
};

const groupStageProblems = (document: TemplateDraftDocument, add: TemplateProblemSink) => {
    if (document.submissionMode !== InformationRequestSubmissionMode.STAGED) return;
    const stageByRequirement = new Map(document.sections.flatMap(section => {
        const stage = section.submissionStageKey?.trim().toLowerCase();
        return stage ? section.requirements.map(requirement => [requirement.requirementKey, stage] as const) : [];
    }));
    const familyOf = (groupKey: string): Set<string> => {
        const family = new Set([groupKey]);
        let grew = true;
        while (grew) {
            const before = family.size;
            document.groups.forEach(group => {
                if (group.parentGroupKey && family.has(group.parentGroupKey.trim())) family.add(group.groupKey);
            });
            grew = family.size > before;
        }
        return family;
    };
    document.groups.forEach((group, groupIndex) => {
        const family = familyOf(group.groupKey);
        const stages = [...new Set(requirementsOf(document)
            .filter(requirement => family.has(anchorOf(requirement) ?? ""))
            .flatMap(requirement => stageByRequirement.get(requirement.requirementKey) ?? []))].sort();
        if (stages.length > 1) {
            add(`Group "${group.groupKey}" and its nested groups are answered in stages ${listed(stages)}, so adding or removing one of its occurrences would change more than one stage.`,
                {panel: "groups", groupIndex});
        }
    });
};

const substituteProblems = (document: TemplateDraftDocument, add: TemplateProblemSink) => {
    const requirements = requirementsOf(document);
    const namedAsAlternative = new Set(requirements.flatMap(requirement => requirement.substituteRequirementKeys ?? []));
    const anchorByKey = new Map(requirements.map(requirement => [requirement.requirementKey, anchorOf(requirement)]));
    document.sections.forEach((section, sectionIndex) => section.requirements.forEach((requirement, requirementIndex) => {
        const report = (message: string) => add(message, {panel: "sections", sectionIndex, requirementIndex});
        const label = requirementLabel(requirement);
        const alternatives = requirement.substituteRequirementKeys ?? [];
        if (alternatives.length > 0 && namedAsAlternative.has(requirement.requirementKey)) {
            report(`Requirement "${label}" stands in for another document, so it cannot name alternatives of its own.`);
        }
        alternatives.forEach(key => {
            const anchor = anchorByKey.get(key);
            if (anchor !== undefined && anchor !== anchorOf(requirement)) {
                report(`Requirement "${label}" names "${key}" as its alternative, but "${key}" repeats per "${anchor}"; an alternative is found only at the top level or in the document's own group.`);
            }
        });
    }));
};

const conditionScopeProblems = (document: TemplateDraftDocument, add: TemplateProblemSink) => {
    const requirements = requirementsOf(document);
    const collectors = collectorsOf(requirements);
    const anchorByKey = new Map(requirements.map(requirement => [requirement.requirementKey, anchorOf(requirement)]));
    const parentByGroup = new Map(document.groups.map(group => [group.groupKey, group.parentGroupKey?.trim() || undefined]));
    requirements.forEach(requirement => {
        const ruleIndex = document.conditionRules.findIndex(rule => rule.ruleKey === requirement.conditionalRuleKey);
        if (ruleIndex < 0) return;
        const rule = document.conditionRules[ruleIndex];
        const readable = new Set<string | undefined>([undefined]);
        for (let cursor = anchorOf(requirement); cursor && !readable.has(cursor); cursor = parentByGroup.get(cursor)) readable.add(cursor);
        sourceKeysOf(rule, collectors).forEach(source => {
            const sourceAnchor = anchorByKey.get(source);
            if (!anchorByKey.has(source) || readable.has(sourceAnchor)) return;
            add(`Condition "${rule.ruleKey}" reads "${source}", which repeats per "${sourceAnchor}", but decides "${requirementLabel(requirement)}", which can read only answers at the top level or in its own or an enclosing group.`,
                {panel: "conditions", ruleIndex});
        });
    });
};

export const dependencyProblems = (document: TemplateDraftDocument, add: TemplateProblemSink) => {
    conditionCycleProblems(document, add);
    groupStageProblems(document, add);
    substituteProblems(document, add);
    conditionScopeProblems(document, add);
};
