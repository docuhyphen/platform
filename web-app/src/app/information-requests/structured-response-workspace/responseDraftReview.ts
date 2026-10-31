import {
    InformationRequestConditionEvaluationDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestResponseDto,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {requirementElementKey} from "../shared/responseElementKeys.ts";
import {responseAnswerText} from "../submission/submissionReview.ts";
import {occurrenceLabel, ResponseAnswerEdits} from "./responseAnswerState.ts";
import {
    AnsweredRequirement,
    answeredRequirements,
    draftPatch,
    occurrenceGroupKeyFromTemplate,
    requirementOccurrenceAnchorKey,
    ResponseEdits,
    responseKey,
} from "./structuredResponseWorkspaceState.ts";

export type ResponseRevisions = Record<string, number>;

export interface ResponseDrafts {
    edits: ResponseEdits;
    answers: ResponseAnswerEdits;
    bases: ResponseRevisions;
}

export type SavedDrafts = Omit<ResponseDrafts, "bases">;

export interface ResponseDraftConflict {
    key: string;
    elementId: string;
    label: string;
    latestAnswer: string;
}

export interface ResponseDraftReview {
    conflicts: ResponseDraftConflict[];
    settledKeys: string[];
}

interface DraftScope {
    occurrences: InformationRequestGroupOccurrenceDto[];
    groups: InformationRequestTemplateGroupDto[];
    requirements: InformationRequestTemplateRequirementDto[];
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>;
}

export const responseRevisions = (
    {occurrences, groups, requirements, conditionByScope}: DraftScope,
    responses: InformationRequestResponseDto[],
): ResponseRevisions =>
    Object.fromEntries(answeredRequirements(occurrences, groups, requirements, responses, conditionByScope)
        .map(answered => [answered.key, answered.response.responseRevision]));

const shownKeys = ({occurrences, groups, requirements}: DraftScope): Set<string> =>
    new Set(occurrences.flatMap(occurrence => {
        const groupKey = occurrenceGroupKeyFromTemplate(occurrence, groups);
        return requirements
            .filter(requirement => requirementOccurrenceAnchorKey(requirement) === groupKey)
            .map(requirement => responseKey(requirement.id, occurrence.occurrencePath));
    }));

export const shownDrafts = (scope: DraftScope, drafts: SavedDrafts, held: ReadonlySet<string> = new Set()): SavedDrafts => {
    const shown = shownKeys(scope);
    const sent = (key: string) => shown.has(key) && !held.has(key);
    return {
        edits: Object.fromEntries(Object.entries(drafts.edits).filter(([key]) => sent(key))),
        answers: Object.fromEntries(Object.entries(drafts.answers).filter(([key]) => sent(key))),
    };
};

export const withSavedResponses = (
    responses: InformationRequestResponseDto[],
    saved: InformationRequestResponseDto[],
): InformationRequestResponseDto[] =>
    responses.map(response => saved.find(candidate =>
        candidate.informationRequestRequirementId === response.informationRequestRequirementId) ?? response);

const changedSinceDraft = ({key, response}: AnsweredRequirement, bases: ResponseRevisions): boolean => {
    const base = bases[key];
    return base === undefined || response.responseRevision > base;
};

const conflictOf = (
    answered: AnsweredRequirement,
    bindings: SchemaFieldBindingDto[],
    labelOf: (occurrencePath: string) => string,
): ResponseDraftConflict => {
    const occurrence = labelOf(answered.occurrencePath);
    return {
        key: answered.key,
        elementId: requirementElementKey(answered.occurrencePath, answered.requirement.requirementKey),
        label: occurrence ? `${answered.requirement.prompt} (${occurrence})` : answered.requirement.prompt,
        latestAnswer: responseAnswerText(answered.response, answered.requirement, bindings),
    };
};

export const reviewResponseDrafts = (
    scope: DraftScope,
    bindings: SchemaFieldBindingDto[],
    responses: InformationRequestResponseDto[],
    drafts: ResponseDrafts,
    labelOf: (occurrencePath: string) => string = occurrenceLabel,
): ResponseDraftReview => {
    const {occurrences, groups, requirements, conditionByScope} = scope;
    const changed = answeredRequirements(occurrences, groups, requirements, responses, conditionByScope)
        .filter(answered => !answered.response.editLock)
        .filter(answered => answered.key in drafts.edits || answered.key in drafts.answers)
        .filter(answered => changedSinceDraft(answered, drafts.bases));
    const differing = changed.filter(answered => draftPatch(answered, bindings, drafts.edits, drafts.answers) !== null);
    return {
        conflicts: differing.map(answered => conflictOf(answered, bindings, labelOf)),
        settledKeys: changed.filter(answered => !differing.includes(answered)).map(answered => answered.key),
    };
};
