import {TemplateDraftDocument} from "./templateDraftDocument.ts";
import {TemplateProblemSink} from "./templateDraftRules.ts";

export const TEMPLATE_TEXT_LIMITS = {
    key: 128,
    title: 255,
    prompt: 1024,
    helpText: 2048,
    acceptedValue: 255,
    compartmentKey: 64,
    conditionValue: 256,
} as const;

const tooLong = (value: string | null | undefined, limit: number): boolean => (value?.trim().length ?? 0) > limit;

const shown = (value: string): string => value.length > 40 ? `${value.slice(0, 40)}...` : value;

const comparedTexts = (value: unknown): string[] =>
    (Array.isArray(value) ? value : [value]).flatMap(entry => typeof entry === "string" ? [entry] : []);

export const textLimitProblems = (document: TemplateDraftDocument, add: TemplateProblemSink) => {
    document.sections.forEach((section, sectionIndex) => {
        const target = {panel: "sections" as const, sectionIndex};
        const label = shown(section.sectionKey);
        if (tooLong(section.sectionKey, TEMPLATE_TEXT_LIMITS.key)) add(`Section "${label}" has a key longer than 128 characters.`, target);
        if (tooLong(section.title, TEMPLATE_TEXT_LIMITS.title)) add(`Section "${label}" has a title longer than 255 characters.`, target);
        if (tooLong(section.helpText, TEMPLATE_TEXT_LIMITS.helpText)) {
            add(`Section "${label}" has help text longer than 2048 characters.`, target);
        }
        section.requirements.forEach((requirement, requirementIndex) => {
            const report = (message: string) => add(message, {panel: "sections", sectionIndex, requirementIndex});
            const name = shown(requirement.requirementKey);
            if (tooLong(requirement.requirementKey, TEMPLATE_TEXT_LIMITS.key)) report(`Requirement "${name}" has a key longer than 128 characters.`);
            if (tooLong(requirement.prompt, TEMPLATE_TEXT_LIMITS.prompt)) report(`Requirement "${name}" has a prompt longer than 1024 characters.`);
            if (tooLong(requirement.helpText, TEMPLATE_TEXT_LIMITS.helpText)) {
                report(`Requirement "${name}" has help text longer than 2048 characters.`);
            }
            if ((requirement.evidencePolicy?.acceptedValues ?? []).some(accepted => tooLong(accepted.acceptedValue, TEMPLATE_TEXT_LIMITS.acceptedValue))) {
                report(`Requirement "${name}" accepts a value longer than 255 characters.`);
            }
            if (tooLong(requirement.confidentialityCompartmentKey, TEMPLATE_TEXT_LIMITS.compartmentKey)) {
                report(`Requirement "${name}" has a confidentiality compartment longer than 64 characters.`);
            }
        });
    });
    document.groups.forEach((group, groupIndex) => {
        if (tooLong(group.groupKey, TEMPLATE_TEXT_LIMITS.key)) {
            add(`Group "${shown(group.groupKey)}" has a key longer than 128 characters.`, {
                panel: "groups",
                groupIndex
            });
        }
    });
    document.conditionRules.forEach((rule, ruleIndex) => {
        if (tooLong(rule.ruleKey, TEMPLATE_TEXT_LIMITS.key)) {
            add(`Condition "${shown(rule.ruleKey)}" has a key longer than 128 characters.`, {
                panel: "conditions",
                ruleIndex
            });
        }
        if (rule.predicates.some(predicate => comparedTexts(predicate.value).some(text => tooLong(text, TEMPLATE_TEXT_LIMITS.conditionValue)))) {
            add(`Condition "${shown(rule.ruleKey)}" compares a value longer than 256 characters.`, {
                panel: "conditions",
                ruleIndex
            });
        }
    });
    document.reviewStages.forEach((stage, stageIndex) => {
        const target = {panel: "review" as const, stageIndex};
        if (tooLong(stage.stageKey, TEMPLATE_TEXT_LIMITS.key)) add(`Review stage "${shown(stage.stageKey)}" has a key longer than 128 characters.`, target);
        if (tooLong(stage.title, TEMPLATE_TEXT_LIMITS.title)) add(`Review stage "${shown(stage.stageKey)}" has a title longer than 255 characters.`, target);
    });
};
