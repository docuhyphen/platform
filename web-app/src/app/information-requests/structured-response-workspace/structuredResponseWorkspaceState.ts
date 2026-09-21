import {
    FieldValueDto,
    InformationRequestConditionEvaluationDto,
    InformationRequestConditionHiddenDataPolicy,
    InformationRequestConditionEvaluationState,
    InformationRequestDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestTemplateConditionRuleDto,
    InformationRequestTemplateGroupDto,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {buildSparseFieldValuePayload} from "../../exchanges/components/exchange-fields-tab/fieldValuePayload.ts";
import {storedFieldValues} from "../../exchanges/components/exchange-fields-tab/fieldEditorState.ts";

export type ResponseEdits = Record<string, Record<string, unknown>>;

export interface HiddenClearConfirmation
{
    requirementId: string;
    prompt: string;
    occurrencePath: string;
}

export const ROOT_OCCURRENCE_PATH = "root";

export const conditionScopeKey = (ruleKey: string, occurrencePath: string): string =>
    `${ruleKey}|${occurrencePath}`;

export const conditionScopeMap = (
    evaluations: InformationRequestConditionEvaluationDto[],
): Map<string, InformationRequestConditionEvaluationDto> =>
    new Map(evaluations.map(evaluation =>
        [conditionScopeKey(evaluation.ruleKey, evaluation.occurrencePath), evaluation]));

export const occurrenceGroupKey = (occurrencePath: string): string =>
    occurrencePath === ROOT_OCCURRENCE_PATH
        ? ROOT_OCCURRENCE_PATH
        : occurrencePath.replace(/\[[0-9]+].*$/, "");

export const occurrenceGroupKeyFromTemplate = (
    occurrence: InformationRequestGroupOccurrenceDto,
    groups: InformationRequestTemplateGroupDto[],
): string =>
    occurrence.occurrencePath === ROOT_OCCURRENCE_PATH
        ? ROOT_OCCURRENCE_PATH
        : groups.find(group => group.id === occurrence.sourceTemplateGroupId)?.groupKey ??
            occurrenceGroupKey(occurrence.occurrencePath);

export const occurrencePresentation = (
    occurrence: InformationRequestGroupOccurrenceDto,
    groups: InformationRequestTemplateGroupDto[],
) =>
{
    const groupKey = occurrenceGroupKeyFromTemplate(occurrence, groups);
    const isRootOccurrence = occurrence.occurrencePath === ROOT_OCCURRENCE_PATH;
    const childGroups = isRootOccurrence ? [] : groups.filter(group => group.parentGroupKey === groupKey);
    return {groupKey, isRootOccurrence, childGroups};
};

export const requirementOccurrenceAnchorKey = (
    requirement: InformationRequestTemplateRequirementDto,
): string =>
    requirement.occurrenceAnchorKey ?? ROOT_OCCURRENCE_PATH;

export const siblingOccurrenceIds = (
    occurrences: InformationRequestGroupOccurrenceDto[],
    occurrence: InformationRequestGroupOccurrenceDto,
): string[] =>
    occurrences
        .filter(candidate => candidate.sourceTemplateGroupId === occurrence.sourceTemplateGroupId)
        .filter(candidate => (candidate.parentOccurrenceId ?? null) === (occurrence.parentOccurrenceId ?? null))
        .map(candidate => candidate.id);

export const responseKey = (requirementId: string, occurrencePath: string): string =>
    `${requirementId}|${occurrencePath}`;

/**
 * A conditional Requirement is answered once per occurrence, so its rule is read for that
 * occurrence and only falls back to the request-wide answer when the rule was evaluated there.
 */
export const isRequirementActive = (
    requirement: InformationRequestTemplateRequirementDto,
    occurrencePath: string,
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>,
): boolean =>
{
    if (!requirement.conditionalRuleKey) return true;
    const evaluation = conditionByScope.get(conditionScopeKey(requirement.conditionalRuleKey, occurrencePath)) ??
        conditionByScope.get(conditionScopeKey(requirement.conditionalRuleKey, ROOT_OCCURRENCE_PATH));
    return evaluation?.state === InformationRequestConditionEvaluationState.TRUE;
};

export const responseForFieldRequirement = (
    responses: InformationRequestResponseDto[],
    requirement: InformationRequestTemplateRequirementDto,
    occurrencePath: string,
): InformationRequestResponseDto | undefined =>
    responses.find(response =>
        response.sourceTemplateBindingId === requirement.id &&
        response.occurrencePath === occurrencePath) ??
    responses.find(response =>
        response.sourceTemplateRequirementId === requirement.templateRequirementId &&
        response.occurrencePath === occurrencePath);

export const firstValueSet = (
    binding: SchemaFieldBindingDto,
    response?: InformationRequestResponseDto,
): FieldValueDto[] =>
    response?.fieldValues ?? [{
        fieldContractId: binding.fieldContractId,
        schemaFieldBindingId: binding.id,
        namespace: binding.namespace,
        fieldKey: binding.fieldKey,
        label: binding.label,
        valueType: binding.valueType,
        isEmpty: true,
        value: null,
    }];

export const shownFieldValues = (
    binding: SchemaFieldBindingDto,
    occurrencePath: string,
    requirementId: string,
    response: InformationRequestResponseDto | undefined,
    edits: ResponseEdits,
): Record<string, unknown> =>
{
    const key = responseKey(requirementId, occurrencePath);
    return {
        ...storedFieldValues([binding], firstValueSet(binding, response)),
        ...(edits[key] ?? {}),
    };
};

export const workspaceOccurrences = (
    request: InformationRequestDto,
    occurrences: InformationRequestGroupOccurrenceDto[],
    requirements: InformationRequestTemplateRequirementDto[],
): InformationRequestGroupOccurrenceDto[] =>
{
    const hasRootRequirements = requirements.some(requirement =>
        requirementOccurrenceAnchorKey(requirement) === ROOT_OCCURRENCE_PATH);
    if (!hasRootRequirements) return occurrences;
    return [
        {
            id: ROOT_OCCURRENCE_PATH,
            informationRequestId: request.id,
            sourceTemplateGroupId: ROOT_OCCURRENCE_PATH,
            occurrenceIndex: 0,
            occurrencePath: ROOT_OCCURRENCE_PATH,
            createdAt: request.createdAt,
        },
        ...occurrences,
    ];
};

export const buildResponsePatches = (
    occurrences: InformationRequestGroupOccurrenceDto[],
    groups: InformationRequestTemplateGroupDto[],
    requirements: InformationRequestTemplateRequirementDto[],
    bindings: SchemaFieldBindingDto[],
    responses: InformationRequestResponseDto[],
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>,
    edits: ResponseEdits,
) =>
    occurrences.flatMap(occurrence =>
        requirements
            .filter(requirement => requirement.requirementType === InformationRequestRequirementType.FIELD)
            .filter(requirement =>
                requirementOccurrenceAnchorKey(requirement) === occurrenceGroupKeyFromTemplate(occurrence, groups))
            .filter(requirement => isRequirementActive(requirement, occurrence.occurrencePath, conditionByScope))
            .map(requirement =>
            {
                const binding = bindings.find(candidate =>
                    candidate.fieldDefinitionId === requirement.collectedFieldDefinitionId);
                if (!binding) return null;
                const response = responseForFieldRequirement(
                    responses,
                    requirement,
                    occurrence.occurrencePath,
                );
                const storedValues = firstValueSet(binding, response);
                const state = shownFieldValues(binding, occurrence.occurrencePath, requirement.id, response, edits);
                const values = buildSparseFieldValuePayload([binding], storedValues, state);
                if (values.length === 0) return null;
                return {
                    requirementId: response?.informationRequestRequirementId ?? requirement.id,
                    disposition: InformationRequestResponseDisposition.PROVIDED,
                    fieldValues: {etag: response?.fieldValueSetETag, values},
                };
            })
            .filter(patch => patch !== null),
    );

export const hiddenClearConfirmations = (
    conditionRules: InformationRequestTemplateConditionRuleDto[],
    evaluations: InformationRequestConditionEvaluationDto[],
    requirements: InformationRequestTemplateRequirementDto[],
    responses: InformationRequestResponseDto[],
): HiddenClearConfirmation[] =>
{
    const clearRuleKeys = new Set(conditionRules
        .filter(rule => rule.hiddenDataPolicy === InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION)
        .map(rule => rule.ruleKey));
    const inactiveScopes = new Set(evaluations
        .filter(evaluation => evaluation.state === InformationRequestConditionEvaluationState.FALSE)
        .filter(evaluation => clearRuleKeys.has(evaluation.ruleKey))
        .map(evaluation => `${evaluation.ruleKey}:${evaluation.occurrencePath}`));

    return requirements.flatMap(requirement =>
    {
        if (!requirement.conditionalRuleKey || !clearRuleKeys.has(requirement.conditionalRuleKey)) return [];
        return responses
            .filter(response => response.sourceTemplateBindingId === requirement.id)
            .filter(response => inactiveScopes.has(`${requirement.conditionalRuleKey}:${response.occurrencePath}`))
            .filter(response =>
                Boolean(response.narrative) ||
                response.fieldValues.some(value => !value.isEmpty))
            .map(response => ({
                requirementId: response.informationRequestRequirementId,
                prompt: requirement.prompt,
                occurrencePath: response.occurrencePath,
            }));
    });
};
