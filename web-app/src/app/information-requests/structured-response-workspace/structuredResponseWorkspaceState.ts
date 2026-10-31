import {
    FieldValueDto,
    InformationRequestConditionEvaluationDto,
    InformationRequestConditionEvaluationState,
    InformationRequestDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestState,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {mayChangeUnderStanding} from "../shared/executionStandingText.ts";
import {buildSparseFieldValuePayload} from "../../exchanges/components/exchange-fields-tab/fieldValuePayload.ts";
import {storedFieldValues} from "../../exchanges/components/exchange-fields-tab/fieldEditorState.ts";
import {
    answersWithValue,
    asksForExplanation,
    currentDisposition,
    ResponseAnswerEdit,
    ResponseAnswerEdits,
} from "./responseAnswerState.ts";

export type ResponseEdits = Record<string, Record<string, unknown>>;

export interface HiddenClearConfirmation
{
    requirementId: string;
    prompt: string;
    occurrencePath: string;
}

export interface OccurrencePresentation {
    groupKey: string;
    isRootOccurrence: boolean;
    childGroups: InformationRequestTemplateGroupDto[];
}

export const ROOT_OCCURRENCE_PATH = "root";

const ANSWERABLE_STATES = new Set([InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS]);

export const acceptsAnswerChanges = (request: InformationRequestDto): boolean =>
    ANSWERABLE_STATES.has(request.state) && mayChangeUnderStanding(request.executionStanding);

export const conditionScopeKey = (ruleKey: string, occurrencePath: string): string =>
    `${ruleKey}|${occurrencePath}`;

export const conditionScopeMap = (
    evaluations: InformationRequestConditionEvaluationDto[],
): Map<string, InformationRequestConditionEvaluationDto> =>
    new Map(evaluations.map(evaluation =>
        [conditionScopeKey(evaluation.ruleKey, evaluation.occurrencePath), evaluation]));

export const occurrenceGroupKeyFromTemplate = (
    occurrence: InformationRequestGroupOccurrenceDto,
    groups: InformationRequestTemplateGroupDto[],
): string | undefined =>
    occurrence.occurrencePath === ROOT_OCCURRENCE_PATH
        ? ROOT_OCCURRENCE_PATH
        : groups.find(group => group.id === occurrence.sourceTemplateGroupId)?.groupKey;

export const occurrencePresentation = (
    occurrence: InformationRequestGroupOccurrenceDto,
    groups: InformationRequestTemplateGroupDto[],
): OccurrencePresentation | undefined =>
{
    const groupKey = occurrenceGroupKeyFromTemplate(occurrence, groups);
    if (groupKey === undefined) return undefined;
    const isRootOccurrence = occurrence.occurrencePath === ROOT_OCCURRENCE_PATH;
    const childGroups = isRootOccurrence ? [] : groups.filter(group => group.parentGroupKey === groupKey);
    return {groupKey, isRootOccurrence, childGroups};
};

export const requirementOccurrenceAnchorKey = (
    requirement: InformationRequestTemplateRequirementDto,
): string =>
    requirement.occurrenceAnchorKey ?? ROOT_OCCURRENCE_PATH;

export const responseKey = (requirementId: string, occurrencePath: string): string =>
    `${requirementId}|${occurrencePath}`;

const scopedConditionEvaluation = (
    ruleKey: string,
    occurrencePath: string,
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>,
): InformationRequestConditionEvaluationDto | undefined =>
    conditionByScope.get(conditionScopeKey(ruleKey, occurrencePath)) ??
    conditionByScope.get(conditionScopeKey(ruleKey, ROOT_OCCURRENCE_PATH));

const returnedByServer = (
    requirement: InformationRequestTemplateRequirementDto,
    occurrencePath: string,
    responses: InformationRequestResponseDto[],
): boolean =>
    responses.some(response =>
        response.sourceTemplateBindingId === requirement.id && response.occurrencePath === occurrencePath);

export const conditionStateOf = (
    requirement: InformationRequestTemplateRequirementDto,
    occurrencePath: string,
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>,
): InformationRequestConditionEvaluationState | undefined =>
    requirement.conditionalRuleKey
        ? scopedConditionEvaluation(requirement.conditionalRuleKey, occurrencePath, conditionByScope)?.state
        : undefined;

export const isRequirementActive = (
    requirement: InformationRequestTemplateRequirementDto,
    occurrencePath: string,
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>,
    responses: InformationRequestResponseDto[],
): boolean =>
{
    if (!requirement.conditionalRuleKey) return true;
    const evaluation = scopedConditionEvaluation(requirement.conditionalRuleKey, occurrencePath, conditionByScope);
    return evaluation
        ? evaluation.state === InformationRequestConditionEvaluationState.TRUE
        : returnedByServer(requirement, occurrencePath, responses);
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

const fieldValueChanges = (
    requirement: InformationRequestTemplateRequirementDto,
    occurrencePath: string,
    bindings: SchemaFieldBindingDto[],
    response: InformationRequestResponseDto | undefined,
    edits: ResponseEdits,
) =>
{
    if (requirement.requirementType !== InformationRequestRequirementType.FIELD) return null;
    const binding = bindings.find(candidate => candidate.fieldDefinitionId === requirement.collectedFieldDefinitionId);
    if (!binding) return null;
    const state = shownFieldValues(binding, occurrencePath, requirement.id, response, edits);
    const values = buildSparseFieldValuePayload([binding], firstValueSet(binding, response), state);
    return values.length === 0 ? null : {etag: response?.fieldValueSetETag, values};
};

const narrativeChange = (answer: ResponseAnswerEdit | undefined, response: InformationRequestResponseDto | undefined) =>
{
    if (answer?.narrative === undefined) return {};
    const narrative = answer.narrative.trim();
    if (narrative === (response?.narrative ?? "").trim()) return {};
    return narrative ? {narrative} : {clearNarrative: true};
};

export interface AnsweredRequirement {
    key: string;
    occurrencePath: string;
    requirement: InformationRequestTemplateRequirementDto;
    response: InformationRequestResponseDto;
}

export const answeredRequirements = (
    occurrences: InformationRequestGroupOccurrenceDto[],
    groups: InformationRequestTemplateGroupDto[],
    requirements: InformationRequestTemplateRequirementDto[],
    responses: InformationRequestResponseDto[],
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>,
): AnsweredRequirement[] =>
    occurrences.flatMap(occurrence => {
        const groupKey = occurrenceGroupKeyFromTemplate(occurrence, groups);
        if (groupKey === undefined) return [];
        return requirements
            .filter(requirement => requirement.requirementType !== InformationRequestRequirementType.RESPONSE_ATTESTATION)
            .filter(requirement => requirementOccurrenceAnchorKey(requirement) === groupKey)
            .filter(requirement => isRequirementActive(requirement, occurrence.occurrencePath, conditionByScope, responses))
            .flatMap(requirement =>
            {
                const response = responseForFieldRequirement(responses, requirement, occurrence.occurrencePath);
                if (!response) return [];
                const key = responseKey(requirement.id, occurrence.occurrencePath);
                return [{key, occurrencePath: occurrence.occurrencePath, requirement, response}];
            });
    });

export const lockedResponseKeys = (
    occurrences: InformationRequestGroupOccurrenceDto[],
    groups: InformationRequestTemplateGroupDto[],
    requirements: InformationRequestTemplateRequirementDto[],
    responses: InformationRequestResponseDto[],
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>,
): string[] =>
    answeredRequirements(occurrences, groups, requirements, responses, conditionByScope)
        .filter(answered => Boolean(answered.response.editLock))
        .map(answered => answered.key);

export const draftPatch = (
    {key, occurrencePath, requirement, response}: AnsweredRequirement,
    bindings: SchemaFieldBindingDto[],
    edits: ResponseEdits,
    answers: ResponseAnswerEdits,
) => {
    const answer = answers[key];
    const disposition = currentDisposition(requirement, response, answer);
    const dispositionChanged = answer?.disposition !== undefined && answer.disposition !== response.disposition;
    const keeping = Boolean(response.reconfirmationRequired && answer?.reconfirmed);
    const fieldValues = answersWithValue(disposition)
        ? fieldValueChanges(requirement, occurrencePath, bindings, response, edits)
        : null;
    const narrative = narrativeChange(answer, response);
    if (!dispositionChanged && !keeping && !fieldValues && Object.keys(narrative).length === 0) return null;
    const answering = dispositionChanged || keeping || Boolean(fieldValues) || asksForExplanation(disposition) ||
        response.disposition !== InformationRequestResponseDisposition.NOT_ANSWERED;
    return {
        requirementId: response.informationRequestRequirementId,
        ...(answering ? {disposition} : {}),
        ...narrative,
        ...(fieldValues ? {fieldValues} : {}),
    };
};

export const awaitsExplanation = (
    answered: AnsweredRequirement,
    bindings: SchemaFieldBindingDto[],
    edits: ResponseEdits,
    answers: ResponseAnswerEdits,
): boolean => {
    const answer = answers[answered.key];
    const disposition = currentDisposition(answered.requirement, answered.response, answer);
    const narrative = answer?.narrative ?? answered.response.narrative ?? "";
    return asksForExplanation(disposition) && narrative.trim() === "" &&
        draftPatch(answered, bindings, edits, answers) !== null;
};

export const explanationsAwaited = (
    occurrences: InformationRequestGroupOccurrenceDto[],
    groups: InformationRequestTemplateGroupDto[],
    requirements: InformationRequestTemplateRequirementDto[],
    bindings: SchemaFieldBindingDto[],
    responses: InformationRequestResponseDto[],
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>,
    edits: ResponseEdits,
    answers: ResponseAnswerEdits,
): string[] =>
    answeredRequirements(occurrences, groups, requirements, responses, conditionByScope)
        .filter(({response}) => !response.editLock)
        .filter(answered => awaitsExplanation(answered, bindings, edits, answers))
        .map(answered => answered.key);

export const buildResponsePatches = (
    occurrences: InformationRequestGroupOccurrenceDto[],
    groups: InformationRequestTemplateGroupDto[],
    requirements: InformationRequestTemplateRequirementDto[],
    bindings: SchemaFieldBindingDto[],
    responses: InformationRequestResponseDto[],
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>,
    edits: ResponseEdits,
    answers: ResponseAnswerEdits = {},
) =>
    answeredRequirements(occurrences, groups, requirements, responses, conditionByScope)
        .filter(({response}) => !response.editLock)
        .filter(answered => !awaitsExplanation(answered, bindings, edits, answers))
        .map(answered => draftPatch(answered, bindings, edits, answers))
        .filter(patch => patch !== null);
