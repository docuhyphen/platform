import {describe, expect, it} from "vitest";
import {WorkflowStepSpecDraft} from "../../../../models/models.tsx";
import {
    deleteStepAndRemap,
    findRoutesReferencingStep,
    findStepsWithInvalidRoutes,
    remapOutcomeForDeletion,
} from "../stepMutations.ts";

// ── helpers ─────────────────────────────────────────────────────────────────

const approval = (
    name: string,
    onApprove?: string,
    onReject?: string,
): WorkflowStepSpecDraft => ({
    name,
    type: "APPROVAL",
    assignees: [],
    quorum: {kind: "ANY"},
    addons: [],
    ...(onApprove !== undefined ? {onApprove: {nextStep: onApprove}} : {}),
    ...(onReject !== undefined ? {onReject: {nextStep: onReject}} : {}),
});

const condition = (name: string, onTrue?: string, onFalse?: string): WorkflowStepSpecDraft => ({
    name,
    type: "CONDITION",
    assignees: [],
    quorum: {kind: "ANY"},
    addons: [],
    ...(onTrue !== undefined ? {onTrue: {nextStep: onTrue}} : {}),
    ...(onFalse !== undefined ? {onFalse: {nextStep: onFalse}} : {}),
});

const wait = (name: string, onApprove?: string): WorkflowStepSpecDraft => ({
    name,
    type: "WAIT_FOR_COUNTERPARTY_CLEARANCE",
    assignees: [],
    quorum: {kind: "ANY"},
    addons: [],
    ...(onApprove !== undefined ? {onApprove: {nextStep: onApprove}} : {}),
});

// ── remapOutcomeForDeletion ───────────────────────────────────────────────────

describe("remapOutcomeForDeletion", () =>
{
    it("rewrites a reference to the deleted step to END", () =>
    {
        expect(remapOutcomeForDeletion({nextStep: "2"}, 2)).toEqual({nextStep: "END"});
    });

    it("decrements a reference to a step after the deleted one", () =>
    {
        expect(remapOutcomeForDeletion({nextStep: "3"}, 1)).toEqual({nextStep: "2"});
    });

    it("leaves a reference to an earlier step unchanged", () =>
    {
        expect(remapOutcomeForDeletion({nextStep: "1"}, 3)).toEqual({nextStep: "1"});
    });

    it("leaves END and undefined outcomes unchanged", () =>
    {
        expect(remapOutcomeForDeletion({nextStep: "END"}, 0)).toEqual({nextStep: "END"});
        expect(remapOutcomeForDeletion(undefined, 0)).toBeUndefined();
    });

    it("preserves the emit event when remapping", () =>
    {
        expect(remapOutcomeForDeletion({nextStep: "3", emit: "exchange.activated"}, 1))
            .toEqual({nextStep: "2", emit: "exchange.activated"});
    });
});

// ── deleteStepAndRemap ────────────────────────────────────────────────────────

describe("deleteStepAndRemap", () =>
{
    it("deleting an unrelated later step preserves existing routes", () =>
    {
        const steps = [approval("A", "1", "END"), approval("B", "END", "END"), approval("C", "END")];
        const result = deleteStepAndRemap(steps, 2);
        expect(result).toHaveLength(2);
        expect(result[0].onApprove).toEqual({nextStep: "1"});
        expect(result[0].onReject).toEqual({nextStep: "END"});
    });

    it("deleting a step before a referenced target decrements the target index", () =>
    {
        // Step 0 routes to step 2; deleting step 1 should leave that route pointing at step 1.
        const steps = [approval("A", "2", "END"), approval("B", "END"), approval("C", "END")];
        const result = deleteStepAndRemap(steps, 1);
        expect(result).toHaveLength(2);
        expect(result[0].onApprove).toEqual({nextStep: "1"});
    });

    it("deleting the referenced target rewrites the outcome to END", () =>
    {
        const steps = [approval("A", "1", "END"), approval("B", "END")];
        const result = deleteStepAndRemap(steps, 1);
        expect(result).toHaveLength(1);
        expect(result[0].onApprove).toEqual({nextStep: "END"});
    });

    it("remaps approval approve and reject routes together", () =>
    {
        const steps = [approval("A", "2", "3"), approval("gone", "END"), approval("C", "END"), approval("D", "END")];
        const result = deleteStepAndRemap(steps, 1);
        expect(result[0].onApprove).toEqual({nextStep: "1"});
        expect(result[0].onReject).toEqual({nextStep: "2"});
    });

    it("remaps condition true and false routes", () =>
    {
        const steps = [condition("A", "2", "1"), approval("gone", "END"), approval("C", "END")];
        const result = deleteStepAndRemap(steps, 1);
        expect(result[0].onTrue).toEqual({nextStep: "1"});
        expect(result[0].onFalse).toEqual({nextStep: "END"});
    });

    it("remaps the wait-for-counterparty route", () =>
    {
        const steps = [wait("A", "2"), approval("gone", "END"), approval("C", "END")];
        const result = deleteStepAndRemap(steps, 1);
        expect(result[0].onApprove).toEqual({nextStep: "1"});
    });

    it("does not mutate the input steps", () =>
    {
        const steps = [approval("A", "1"), approval("B", "END")];
        const snapshot = JSON.stringify(steps);
        deleteStepAndRemap(steps, 1);
        expect(JSON.stringify(steps)).toEqual(snapshot);
    });
});

// ── findRoutesReferencingStep ─────────────────────────────────────────────────

describe("findRoutesReferencingStep", () =>
{
    it("lists every route that points at the target step", () =>
    {
        const steps = [
            approval("A", "2", "END"),
            condition("B", "2", "0"),
            approval("target", "END"),
        ];
        const routes = findRoutesReferencingStep(steps, 2);
        expect(routes).toHaveLength(2);
        expect(routes).toEqual(expect.arrayContaining([
            {stepIndex: 0, stepName: "A", outcomeLabel: "On approve"},
            {stepIndex: 1, stepName: "B", outcomeLabel: "If condition is true"},
        ]));
    });

    it("returns an empty list when no route references the target", () =>
    {
        const steps = [approval("A", "END", "END"), approval("target", "END")];
        expect(findRoutesReferencingStep(steps, 1)).toEqual([]);
    });

    it("does not report the target step's own self-reference", () =>
    {
        const steps = [approval("A", "END"), approval("self", "1")];
        expect(findRoutesReferencingStep(steps, 1)).toEqual([]);
    });
});

// ── findStepsWithInvalidRoutes ────────────────────────────────────────────────

describe("findStepsWithInvalidRoutes", () =>
{
    it("flags steps that route to an out-of-range index", () =>
    {
        const steps = [approval("A", "5", "END"), approval("B", "END")];
        expect(findStepsWithInvalidRoutes(steps)).toEqual(new Set([0]));
    });

    it("returns an empty set when all routes are in range", () =>
    {
        const steps = [approval("A", "1", "END"), approval("B", "END")];
        expect(findStepsWithInvalidRoutes(steps).size).toBe(0);
    });
});
