import { describe, expect, it } from "vitest";
import { validateWorkflowGraph } from "../workflowGraphValidation.ts";
import { WorkflowGraph } from "../workflowGraphModels.ts";

// ── helpers ───────────────────────────────────────────────────────────────────

const makeGraph = (partial: Partial<WorkflowGraph>): WorkflowGraph => ({
    nodes: [],
    edges: [],
    warnings: [],
    ...partial,
});

// ── valid graph ───────────────────────────────────────────────────────────────

describe("validateWorkflowGraph: valid graph", () =>
{
    it("returns no warnings for a simple Start -> Step -> End graph", () =>
    {
        const result = validateWorkflowGraph(makeGraph({
            nodes: [
                { id: "start", kind: "START", label: "Start", state: "DEFINITION", details: [], accessibilityLabel: "Start" },
                { id: "s0", kind: "STEP", label: "Step 1", state: "DEFINITION", details: [], accessibilityLabel: "Step 1" },
                { id: "end", kind: "END", label: "End", state: "DEFINITION", details: [], accessibilityLabel: "End" },
            ],
            edges: [
                { id: "e1", source: "start", target: "s0", outcome: "DEFAULT", state: "DEFAULT" },
                { id: "e2", source: "s0", target: "end", outcome: "APPROVE", state: "DEFAULT" },
            ],
        }));
        expect(result.warnings).toHaveLength(0);
    });
});

// ── unreachable steps ─────────────────────────────────────────────────────────

describe("validateWorkflowGraph: unreachable steps", () =>
{
    it("warns about a step not reachable from Start", () =>
    {
        const result = validateWorkflowGraph(makeGraph({
            nodes: [
                { id: "start", kind: "START", label: "Start", state: "DEFINITION", details: [], accessibilityLabel: "Start" },
                { id: "s0", kind: "STEP", label: "Step 1", state: "DEFINITION", details: [], accessibilityLabel: "Step 1" },
                { id: "orphan", kind: "STEP", label: "Orphan", state: "DEFINITION", details: [], accessibilityLabel: "Orphan" },
                { id: "end", kind: "END", label: "End", state: "DEFINITION", details: [], accessibilityLabel: "End" },
            ],
            edges: [
                { id: "e1", source: "start", target: "s0", outcome: "DEFAULT", state: "DEFAULT" },
                { id: "e2", source: "s0", target: "end", outcome: "APPROVE", state: "DEFAULT" },
            ],
        }));
        expect(result.warnings.some(w => w.includes("not reachable") && w.includes("Orphan"))).toBe(true);
    });
});

// ── no path to End ────────────────────────────────────────────────────────────

describe("validateWorkflowGraph: no path to End", () =>
{
    it("warns about a step with no path to End", () =>
    {
        const result = validateWorkflowGraph(makeGraph({
            nodes: [
                { id: "start", kind: "START", label: "Start", state: "DEFINITION", details: [], accessibilityLabel: "Start" },
                { id: "s0", kind: "STEP", label: "Step 1", state: "DEFINITION", details: [], accessibilityLabel: "Step 1" },
                { id: "s1", kind: "STEP", label: "Step 2", state: "DEFINITION", details: [], accessibilityLabel: "Step 2" },
                { id: "end", kind: "END", label: "End", state: "DEFINITION", details: [], accessibilityLabel: "End" },
            ],
            edges: [
                { id: "e1", source: "start", target: "s0", outcome: "DEFAULT", state: "DEFAULT" },
                { id: "e2", source: "s0", target: "end", outcome: "APPROVE", state: "DEFAULT" },
                // s1 has no outgoing edge to End
                { id: "e3", source: "start", target: "s1", outcome: "DEFAULT", state: "DEFAULT" },
            ],
        }));
        expect(result.warnings.some(w => w.includes("no path to the end") && w.includes("Step 2"))).toBe(true);
    });
});

// ── cycles ────────────────────────────────────────────────────────────────────

describe("validateWorkflowGraph: cycles", () =>
{
    it("warns when a cycle is detected", () =>
    {
        const result = validateWorkflowGraph(makeGraph({
            nodes: [
                { id: "start", kind: "START", label: "Start", state: "DEFINITION", details: [], accessibilityLabel: "Start" },
                { id: "s0", kind: "STEP", label: "Step 1", state: "DEFINITION", details: [], accessibilityLabel: "Step 1" },
                { id: "s1", kind: "STEP", label: "Step 2", state: "DEFINITION", details: [], accessibilityLabel: "Step 2" },
                { id: "end", kind: "END", label: "End", state: "DEFINITION", details: [], accessibilityLabel: "End" },
            ],
            edges: [
                { id: "e1", source: "start", target: "s0", outcome: "DEFAULT", state: "DEFAULT" },
                { id: "e2", source: "s0", target: "s1", outcome: "APPROVE", state: "DEFAULT" },
                { id: "e3", source: "s1", target: "s0", outcome: "REJECT", state: "DEFAULT" }, // cycle
                { id: "e4", source: "s1", target: "end", outcome: "APPROVE", state: "DEFAULT" },
            ],
        }));
        expect(result.warnings.some(w => w.toLowerCase().includes("cycle"))).toBe(true);
    });

    it("does not warn about cycle when the backward edge is not present", () =>
    {
        const result = validateWorkflowGraph(makeGraph({
            nodes: [
                { id: "start", kind: "START", label: "Start", state: "DEFINITION", details: [], accessibilityLabel: "Start" },
                { id: "s0", kind: "STEP", label: "Step 1", state: "DEFINITION", details: [], accessibilityLabel: "Step 1" },
                { id: "end", kind: "END", label: "End", state: "DEFINITION", details: [], accessibilityLabel: "End" },
            ],
            edges: [
                { id: "e1", source: "start", target: "s0", outcome: "DEFAULT", state: "DEFAULT" },
                { id: "e2", source: "s0", target: "end", outcome: "APPROVE", state: "DEFAULT" },
            ],
        }));
        expect(result.warnings.some(w => w.toLowerCase().includes("cycle"))).toBe(false);
    });
});

// ── ambiguous transitions ─────────────────────────────────────────────────────

describe("validateWorkflowGraph: ambiguous transitions", () =>
{
    it("warns when a step has two edges sharing the same outcome", () =>
    {
        const result = validateWorkflowGraph(makeGraph({
            nodes: [
                { id: "start", kind: "START", label: "Start", state: "DEFINITION", details: [], accessibilityLabel: "Start" },
                { id: "s0", kind: "STEP", label: "Step 1", state: "DEFINITION", details: [], accessibilityLabel: "Step 1" },
                { id: "s1", kind: "STEP", label: "Step 2", state: "DEFINITION", details: [], accessibilityLabel: "Step 2" },
                { id: "end", kind: "END", label: "End", state: "DEFINITION", details: [], accessibilityLabel: "End" },
            ],
            edges: [
                { id: "e1", source: "start", target: "s0", outcome: "DEFAULT", state: "DEFAULT" },
                { id: "e2", source: "s0", target: "s1", outcome: "APPROVE", state: "DEFAULT" },
                { id: "e3", source: "s0", target: "end", outcome: "APPROVE", state: "DEFAULT" }, // conflicting APPROVE
                { id: "e4", source: "s1", target: "end", outcome: "APPROVE", state: "DEFAULT" },
            ],
        }));
        expect(result.warnings.some(w => w.toLowerCase().includes("conflicting"))).toBe(true);
    });

    it("does not warn when two distinct outcomes point at the same target", () =>
    {
        const result = validateWorkflowGraph(makeGraph({
            nodes: [
                { id: "start", kind: "START", label: "Start", state: "DEFINITION", details: [], accessibilityLabel: "Start" },
                { id: "s0", kind: "STEP", label: "Step 1", state: "DEFINITION", details: [], accessibilityLabel: "Step 1" },
                { id: "end", kind: "END", label: "End", state: "DEFINITION", details: [], accessibilityLabel: "End" },
            ],
            edges: [
                { id: "e1", source: "start", target: "s0", outcome: "DEFAULT", state: "DEFAULT" },
                { id: "e2", source: "s0", target: "end", outcome: "APPROVE", state: "DEFAULT" },
                { id: "e3", source: "s0", target: "end", outcome: "REJECT", state: "DEFAULT" },
            ],
        }));
        expect(result.warnings.some(w => w.toLowerCase().includes("conflicting"))).toBe(false);
    });
});

// ── cycle without a path to End ───────────────────────────────────────────────

describe("validateWorkflowGraph: cycle without a path to End", () =>
{
    it("reports both a cycle and a no-path-to-End warning", () =>
    {
        const result = validateWorkflowGraph(makeGraph({
            nodes: [
                { id: "start", kind: "START", label: "Start", state: "DEFINITION", details: [], accessibilityLabel: "Start" },
                { id: "s0", kind: "STEP", label: "Step 1", state: "DEFINITION", details: [], accessibilityLabel: "Step 1" },
                { id: "s1", kind: "STEP", label: "Step 2", state: "DEFINITION", details: [], accessibilityLabel: "Step 2" },
                { id: "end", kind: "END", label: "End", state: "DEFINITION", details: [], accessibilityLabel: "End" },
            ],
            edges: [
                { id: "e1", source: "start", target: "s0", outcome: "DEFAULT", state: "DEFAULT" },
                { id: "e2", source: "s0", target: "s1", outcome: "APPROVE", state: "DEFAULT" },
                { id: "e3", source: "s1", target: "s0", outcome: "APPROVE", state: "DEFAULT" }, // closed loop, never reaches End
            ],
        }));
        expect(result.warnings.some(w => w.toLowerCase().includes("cycle"))).toBe(true);
        expect(result.warnings.some(w => w.includes("no path to the end"))).toBe(true);
    });
});

// ── adapter warnings are preserved ───────────────────────────────────────────

describe("validateWorkflowGraph: adapter warnings passthrough", () =>
{
    it("includes adapter warnings from graph.warnings", () =>
    {
        const result = validateWorkflowGraph(makeGraph({
            nodes: [
                { id: "start", kind: "START", label: "Start", state: "DEFINITION", details: [], accessibilityLabel: "Start" },
                { id: "end", kind: "END", label: "End", state: "DEFINITION", details: [], accessibilityLabel: "End" },
            ],
            edges: [{ id: "e1", source: "start", target: "end", outcome: "DEFAULT", state: "DEFAULT" }],
            warnings: ["Pre-existing warning from adapter."],
        }));
        expect(result.warnings).toContain("Pre-existing warning from adapter.");
    });
});
