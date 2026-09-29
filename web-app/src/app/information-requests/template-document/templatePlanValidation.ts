import {
    FieldOperator,
    InformationRequestReviewAggregation,
    InformationRequestReviewTieResolution,
} from "../../models/models.tsx";
import {TemplateDraftDocument} from "./templateDraftDocument.ts";
import {
    keyShapeMessage,
    MACHINE_KEY_PATTERN,
    requirementLabel,
    routesReview,
    TemplateProblemSink,
} from "./templateDraftRules.ts";

export const VALUELESS_OPERATORS: ReadonlySet<FieldOperator> = new Set([FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY]);
export const DISPOSITION_OPERATORS: FieldOperator[] = [
    FieldOperator.EQUALS,
    FieldOperator.NOT_EQUALS,
    FieldOperator.IS_EMPTY,
    FieldOperator.IS_NOT_EMPTY,
];

export const groupProblems = (document: TemplateDraftDocument, add: TemplateProblemSink) =>
{
    const keys = new Set<string>();
    const parentByKey = new Map(document.groups.map(group => [group.groupKey, group.parentGroupKey]));
    document.groups.forEach((group, groupIndex) =>
    {
        const target = {panel: "groups" as const, groupIndex};
        if (!MACHINE_KEY_PATTERN.test(group.groupKey)) add(keyShapeMessage("Group key", group.groupKey), target);
        else if (keys.has(group.groupKey)) add(`Two groups use the key "${group.groupKey}".`, target);
        keys.add(group.groupKey);
        const parent = group.parentGroupKey?.trim();
        if (parent === group.groupKey) add(`Group "${group.groupKey}" cannot be nested in itself.`, target);
        else if (parent && !parentByKey.has(parent)) add(`Group "${group.groupKey}" is nested in "${parent}", which is not a group of this Template.`, target);
        else if (parent)
        {
            const visited = new Set([group.groupKey]);
            let cursor: string | undefined = parent;
            while (cursor && !visited.has(cursor))
            {
                visited.add(cursor);
                cursor = parentByKey.get(cursor)?.trim() || undefined;
            }
            if (cursor) add(`Group "${group.groupKey}" is nested inside itself through "${parent}".`, target);
        }
        const minimum = group.minOccurrences ?? 0;
        if (minimum < 0) add(`Group "${group.groupKey}" cannot require a negative number of occurrences.`, target);
        if (group.maxOccurrences !== undefined && group.maxOccurrences < minimum)
        {
            add(`Group "${group.groupKey}" allows fewer occurrences than it requires.`, target);
        }
    });
};

export const conditionProblems = (document: TemplateDraftDocument, add: TemplateProblemSink) =>
{
    const requirementKeys = new Set(document.sections.flatMap(section =>
        section.requirements.map(requirement => requirement.requirementKey)));
    const collectedFields = new Set(document.sections.flatMap(section =>
        section.requirements.flatMap(requirement =>
            requirement.collectedFieldDefinitionId ? [requirement.collectedFieldDefinitionId] : [])));
    const keys = new Set<string>();
    document.conditionRules.forEach((rule, ruleIndex) =>
    {
        const target = {panel: "conditions" as const, ruleIndex};
        const key = rule.ruleKey.trim();
        if (!key) add("A condition needs a key.", target);
        else if (keys.has(key)) add(`Two conditions use the key "${key}".`, target);
        keys.add(key);
        if (rule.predicates.length === 0) add(`Condition "${key}" states no predicates.`, target);
        rule.predicates.forEach(predicate =>
        {
            const source = predicate.sourceRequirementKey?.trim();
            if (Boolean(source) === Boolean(predicate.fieldDefinitionId))
            {
                add(`Condition "${key}" must read exactly one requirement or Field.`, target);
            }
            else if (source && !requirementKeys.has(source))
            {
                add(`Condition "${key}" reads "${source}", which is not a requirement of this Template.`, target);
            }
            else if (source && !DISPOSITION_OPERATORS.includes(predicate.operator))
            {
                add(`Condition "${key}" compares an answer with an operator answers do not support.`, target);
            }
            else if (source && !VALUELESS_OPERATORS.has(predicate.operator) && !predicate.expectedDisposition)
            {
                add(`Condition "${key}" compares an answer but states no answer to compare with.`, target);
            }
            else if (predicate.fieldDefinitionId && !predicate.valueType)
            {
                add(`Condition "${key}" reads a Field but states no value type.`, target);
            }
            else if (predicate.fieldDefinitionId && !collectedFields.has(predicate.fieldDefinitionId))
            {
                add(`Condition "${key}" reads a Field no requirement of this Template collects.`, target);
            }
            else if (predicate.fieldDefinitionId && !VALUELESS_OPERATORS.has(predicate.operator)
                && (predicate.value === undefined || predicate.value === null || predicate.value === ""))
            {
                add(`Condition "${key}" compares a Field but states no value.`, target);
            }
        });
    });
};

export const reviewProblems = (document: TemplateDraftDocument, add: TemplateProblemSink) =>
{
    const sectionKeys = new Set(document.sections.map(section => section.sectionKey));
    const keys = new Set<string>();
    document.reviewStages.forEach((stage, stageIndex) =>
    {
        const target = {panel: "review" as const, stageIndex};
        const label = stage.title.trim() || stage.stageKey;
        if (!MACHINE_KEY_PATTERN.test(stage.stageKey)) add(keyShapeMessage("Review stage key", stage.stageKey), target);
        else if (keys.has(stage.stageKey)) add(`Two review stages use the key "${stage.stageKey}".`, target);
        keys.add(stage.stageKey);
        if (!stage.title.trim()) add(`Review stage "${label}" needs a title.`, target);
        const reviewers = stage.minimumReviewerCount ?? 1;
        if (reviewers < 1) add(`Review stage "${label}" needs at least one reviewer.`, target);
        const quorum = stage.quorumCount;
        const byQuorum = stage.aggregation === InformationRequestReviewAggregation.QUORUM;
        if (byQuorum && quorum === undefined) add(`Review stage "${label}" decides by quorum, so state how many reviewers make one.`, target);
        if (!byQuorum && quorum !== undefined) add(`Review stage "${label}" does not decide by quorum, so it states no quorum count.`, target);
        if (quorum !== undefined && byQuorum && (quorum < 1 || quorum > reviewers))
        {
            add(`Review stage "${label}" needs a quorum between one and its minimum reviewer count.`, target);
        }
        if (stage.tieResolution === InformationRequestReviewTieResolution.REQUIRE_OVERRIDE && !stage.overridePermitted)
        {
            add(`Review stage "${label}" resolves a tie by override, so it permits an override.`, target);
        }
        (stage.sectionKeys ?? []).filter(key => !sectionKeys.has(key)).forEach(key =>
            add(`Review stage "${label}" covers section "${key}", which is not a section of this Template.`, target));
    });
    const routed = document.sections.flatMap(section =>
        section.requirements.filter(routesReview).map(requirement => ({section, requirement})));
    if (routed.length === 0 && document.reviewStages.length > 0)
    {
        add("No requirement is reviewed, so remove the review stages.", {panel: "review"});
    }
    if (document.reviewStages.length === 0) return;
    routed.forEach(({section, requirement}) =>
    {
        const covered = document.reviewStages.some(stage =>
            (stage.sectionKeys ?? []).length === 0 || (stage.sectionKeys ?? []).includes(section.sectionKey));
        if (!covered)
        {
            add(`Requirement "${requirementLabel(requirement)}" is reviewed, but no review stage covers section "${section.title.trim() || section.sectionKey}".`,
                {panel: "review"});
        }
    });
};
