import {InformationRequestResponseDto, InformationRequestTemplateRequirementDto} from "../../models/models.tsx";
import {occurrenceLabel} from "./responseAnswerState.ts";

export interface RefusedResponseItem {
    requirementId: string;
    label: string;
    detail: string;
    occurrencePath: string;
    requirementKey: string;
}

interface StatedProblem {
    requirementId?: unknown;
    occurrencePath?: unknown;
    message?: unknown;
}

const problemsOf = (refusal: unknown): StatedProblem[] => {
    if (typeof refusal !== "object" || refusal === null) return [];
    const problems = (refusal as { problems?: unknown }).problems;
    return Array.isArray(problems)
        ? problems.filter((problem): problem is StatedProblem => typeof problem === "object" && problem !== null)
        : [];
};

const itemOf = (
    problem: StatedProblem,
    requirements: InformationRequestTemplateRequirementDto[],
    responses: InformationRequestResponseDto[],
    labelOf: (occurrencePath: string) => string,
): RefusedResponseItem | null => {
    const response = responses.find(candidate => candidate.informationRequestRequirementId === problem.requirementId);
    const requirement = response && requirements.find(candidate => candidate.id === response.sourceTemplateBindingId);
    if (!response || !requirement) return null;
    const occurrencePath = typeof problem.occurrencePath === "string" && problem.occurrencePath
        ? problem.occurrencePath
        : response.occurrencePath;
    return {
        requirementId: response.informationRequestRequirementId,
        label: labelOf(occurrencePath) ? `${requirement.prompt} (${labelOf(occurrencePath)})` : requirement.prompt,
        detail: typeof problem.message === "string" ? problem.message.trim() : "",
        occurrencePath,
        requirementKey: requirement.requirementKey,
    };
};

export const refusedResponseItems = (
    refusal: unknown,
    requirements: InformationRequestTemplateRequirementDto[],
    responses: InformationRequestResponseDto[],
    labelOf: (occurrencePath: string) => string = occurrenceLabel,
): RefusedResponseItem[] =>
    problemsOf(refusal)
        .map(problem => itemOf(problem, requirements, responses, labelOf))
        .filter((item): item is RefusedResponseItem => item !== null)
        .filter((item, index, all) =>
            all.findIndex(other => other.requirementId === item.requirementId && other.detail === item.detail) === index);

export const withoutNamedProblems = (refusal: unknown, items: RefusedResponseItem[]): unknown => {
    if (items.length === 0 || typeof refusal !== "object" || refusal === null) return refusal;
    const named = new Set(items.map(item => item.requirementId));
    return {
        ...refusal,
        problems: problemsOf(refusal).filter(problem => typeof problem.requirementId !== "string" || !named.has(problem.requirementId)),
    };
};
