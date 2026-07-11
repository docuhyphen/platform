/**
 * Pure helpers for mutating a workflow step list while keeping numeric outcome references
 * consistent. Outcomes store the next step as a string ("END" or a numeric index), so removing a
 * step must rewrite references to it and shift references to later steps down by one. Keeping this
 * logic in one place means the designer and its tests share a single source of truth.
 */

import {StepOutcomeSpecDraft, WorkflowStepSpecDraft} from "../../../models/models.tsx";

const OUTCOME_KEYS = ["onApprove", "onReject", "onTrue", "onFalse"] as const;
type OutcomeKey = (typeof OUTCOME_KEYS)[number];

/** A route that points at a specific step, described for the deletion confirmation. */
export interface AffectedRoute
{
    stepIndex: number;
    stepName?: string;
    outcomeLabel: string;
}

const END = "END";

/** Parses an outcome target into a step index, or null for "END", empty, or malformed values. */
const parseTarget = (nextStep: string | undefined): number | null =>
{
    if (nextStep === undefined || nextStep === "" || nextStep.toUpperCase() === END) return null;
    const parsed = Number(nextStep);
    return Number.isInteger(parsed) && parsed >= 0 ? parsed : null;
};

/** Human-readable label for a step's outcome branch, used in the route-impact list. */
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

/**
 * Rewrites a single outcome for the removal of the step at `deletedIndex`. A reference to the
 * deleted step becomes "END"; a reference to a later step decrements by one; earlier references and
 * "END" are unchanged.
 */
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

/** Lists every outcome across all steps that currently routes to `targetIndex`. */
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

/**
 * Removes the step at `deletedIndex` and remaps every surviving step's outcome references so no
 * route points at the wrong step or an out-of-range index.
 */
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

/** Returns the indexes of steps whose outcomes reference a non-existent (out-of-range) step. */
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

/** Replaces the step at `index` with `step`. */
export const replaceStep = (
    steps: WorkflowStepSpecDraft[],
    index: number,
    step: WorkflowStepSpecDraft,
): WorkflowStepSpecDraft[] => steps.map((s, i) => (i === index ? step : s));

/** Appends `step` to the end of the list. */
export const appendStep = (
    steps: WorkflowStepSpecDraft[],
    step: WorkflowStepSpecDraft,
): WorkflowStepSpecDraft[] => [...steps, step];
