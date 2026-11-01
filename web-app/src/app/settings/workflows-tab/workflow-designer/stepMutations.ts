
import {StepOutcomeSpecDraft, WorkflowStepSpecDraft} from "../../../models/models.tsx";

const OUTCOME_KEYS = ["onApprove", "onReject", "onTrue", "onFalse"] as const;
type OutcomeKey = (typeof OUTCOME_KEYS)[number];

export interface AffectedRoute
{
    stepIndex: number;
    stepName?: string;
    outcomeLabel: string;
}

const END = "END";

const parseTarget = (nextStep: string | undefined): number | null =>
{
    if (nextStep === undefined || nextStep === "" || nextStep.toUpperCase() === END) return null;
    const parsed = Number(nextStep);
    return Number.isInteger(parsed) && parsed >= 0 ? parsed : null;
};

const outcomeLabelFor = (step: WorkflowStepSpecDraft, key: OutcomeKey): string =>
{
    switch (key)
    {
        case "onReject":
            return "On reject";
        case "onTrue":
            return "If condition is true";
        case "onFalse":
            return "If condition is false";
        case "onApprove":
            if (step.type === "CONDITION") return "On approve";
            if (step.type === "WAIT_FOR_COUNTERPARTY_CLEARANCE") return "When unblocked";
            if (step.type === "APPROVAL") return "On approve";
            return "Continue";
    }
};

export const remapOutcomeForDeletion = (
    outcome: StepOutcomeSpecDraft | undefined,
    deletedIndex: number,
): StepOutcomeSpecDraft | undefined =>
{
    if (!outcome) return outcome;
    const target = parseTarget(outcome.nextStep);
    if (target === null) return outcome;
    if (target === deletedIndex) return {...outcome, nextStep: END};
    if (target > deletedIndex) return {...outcome, nextStep: String(target - 1)};
    return outcome;
};

export const findRoutesReferencingStep = (
    steps: WorkflowStepSpecDraft[],
    targetIndex: number,
): AffectedRoute[] =>
{
    const routes: AffectedRoute[] = [];
    steps.forEach((step, stepIndex) =>
    {
        if (stepIndex === targetIndex) return;
        OUTCOME_KEYS.forEach(key =>
        {
            if (parseTarget(step[key]?.nextStep) === targetIndex)
            {
                routes.push({stepIndex, stepName: step.name, outcomeLabel: outcomeLabelFor(step, key)});
            }
        });
    });
    return routes;
};

export const deleteStepAndRemap = (
    steps: WorkflowStepSpecDraft[],
    deletedIndex: number,
): WorkflowStepSpecDraft[] =>
    steps
        .filter((_, i) => i !== deletedIndex)
        .map(step =>
        {
            const next = {...step};
            OUTCOME_KEYS.forEach(key =>
            {
                if (next[key]) next[key] = remapOutcomeForDeletion(next[key], deletedIndex);
            });
            return next;
        });

export const findStepsWithInvalidRoutes = (steps: WorkflowStepSpecDraft[]): Set<number> =>
{
    const invalid = new Set<number>();
    steps.forEach((step, index) =>
    {
        OUTCOME_KEYS.forEach(key =>
        {
            const target = parseTarget(step[key]?.nextStep);
            if (target !== null && target >= steps.length) invalid.add(index);
        });
    });
    return invalid;
};

export const replaceStep = (
    steps: WorkflowStepSpecDraft[],
    index: number,
    step: WorkflowStepSpecDraft,
): WorkflowStepSpecDraft[] => steps.map((s, i) => (i === index ? step : s));

export const appendStep = (
    steps: WorkflowStepSpecDraft[],
    step: WorkflowStepSpecDraft,
): WorkflowStepSpecDraft[] => [...steps, step];
