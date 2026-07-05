import { describe, expect, it } from "vitest";
import { WorkflowDesignerState, WorkflowStepSpecDraft } from "../../../../models/models.tsx";
import { buildDefinitionGraph } from "../workflowDefinitionGraphAdapter.ts";
import { WEBHOOK_DELIVER_HANDLER_KEY } from "../workflowGraphModels.ts";

// ── helpers ───────────────────────────────────────────────────────────────────

const baseState = (
    steps: WorkflowStepSpecDraft[] = [],
    id = "def-1",
): WorkflowDesignerState => ({
    id,
    name: "Test",
    summary: "",
    generalTags: [],
    triggerEvent: "test.event",
    isActive: true,
    steps,
});

const approvalStep = (overrides: Partial<WorkflowStepSpecDraft> = {}): WorkflowStepSpecDraft => ({
    type: "APPROVAL",
    assignees: [],
    quorum: { kind: "ANY" },
    addons: [],
    ...overrides,
});

// ── empty workflow ─────────────────────────────────────────────────────────────

describe("buildDefinitionGraph: empty workflow", () =>
{
    it("produces Start, End, and a direct Start-to-End edge", () =>
    {
        const graph = buildDefinitionGraph(baseState([]));
        expect(graph.nodes.some(n => n.kind === "START")).toBe(true);
        expect(graph.nodes.some(n => n.kind === "END")).toBe(true);
        expect(graph.edges).toHaveLength(1);
        const edge = graph.edges[0];
        expect(edge.source).toMatch(/start$/);
        expect(edge.target).toMatch(/end$/);
        expect(graph.warnings).toHaveLength(0);
    });
});

// ── single step ───────────────────────────────────────────────────────────────

describe("buildDefinitionGraph: single step", () =>
{
    it("connects Start -> step 0 -> End when onApprove is END", () =>
    {
        const graph = buildDefinitionGraph(
            baseState([approvalStep({ onApprove: { nextStep: "END" } })]),
        );
        const nodeIds = graph.nodes.map(n => n.id);
        expect(nodeIds.some(id => id.includes(":step:0"))).toBe(true);
        const startToStep = graph.edges.find(e => e.source.includes(":start"));
        const stepToEnd = graph.edges.find(
            e => e.source.includes(":step:0") && e.target.includes(":end"),
        );
        expect(startToStep).toBeDefined();
        expect(stepToEnd).toBeDefined();
        expect(graph.warnings).toHaveLength(0);
    });

    it("defaults to End when onApprove is absent", () =>
    {
        const graph = buildDefinitionGraph(baseState([approvalStep()]));
        const stepToEnd = graph.edges.find(
            e => e.source.includes(":step:0") && e.target.includes(":end"),
        );
        expect(stepToEnd).toBeDefined();
    });
});

// ── approval branches ─────────────────────────────────────────────────────────

describe("buildDefinitionGraph: approval branches", () =>
{
    it("creates approve and reject edges when both configured", () =>
    {
        const graph = buildDefinitionGraph(
            baseState([
                approvalStep({
                    onApprove: { nextStep: "END" },
                    onReject: { nextStep: "END" },
                }),
            ]),
        );
        const approve = graph.edges.find(e => e.outcome === "APPROVE");
        const reject = graph.edges.find(e => e.outcome === "REJECT");
        expect(approve).toBeDefined();
        expect(reject).toBeDefined();
    });

    it("does not create reject edge when onReject is absent", () =>
    {
        const graph = buildDefinitionGraph(
            baseState([approvalStep({ onApprove: { nextStep: "END" } })]),
        );
        const reject = graph.edges.find(e => e.outcome === "REJECT");
        expect(reject).toBeUndefined();
    });
});

// ── condition branches ────────────────────────────────────────────────────────

describe("buildDefinitionGraph: condition branches", () =>
{
    const conditionStep = (overrides: Partial<WorkflowStepSpecDraft> = {}): WorkflowStepSpecDraft => ({
        type: "CONDITION",
        assignees: [],
        quorum: { kind: "ANY" },
        addons: [],
        predicateExpression: "$subject.status == 'ACTIVE'",
        onTrue: { nextStep: "END" },
        onFalse: { nextStep: "END" },
        ...overrides,
    });

    it("creates TRUE and FALSE edges", () =>
    {
        const graph = buildDefinitionGraph(baseState([conditionStep()]));
        const trueEdge = graph.edges.find(e => e.outcome === "TRUE");
        const falseEdge = graph.edges.find(e => e.outcome === "FALSE");
        expect(trueEdge).toBeDefined();
        expect(falseEdge).toBeDefined();
    });

    it("defaults both outcomes to End when missing", () =>
    {
        const graph = buildDefinitionGraph(
            baseState([{ ...conditionStep(), onTrue: undefined, onFalse: undefined }]),
        );
        const toEnd = graph.edges.filter(e => e.target.includes(":end"));
        expect(toEnd.length).toBeGreaterThanOrEqual(2);
    });
});

// ── WAIT_FOR_COUNTERPARTY_CLEARANCE ───────────────────────────────────────────

describe("buildDefinitionGraph: WAIT_FOR_COUNTERPARTY_CLEARANCE", () =>
{
    const waitStep = (): WorkflowStepSpecDraft => ({
        type: "WAIT_FOR_COUNTERPARTY_CLEARANCE",
        assignees: [],
        quorum: { kind: "ANY" },
        addons: [],
        onApprove: { nextStep: "END" },
    });

    it("produces exactly one neutral Continue edge, no rejection edge", () =>
    {
        const graph = buildDefinitionGraph(baseState([waitStep()]));
        const outgoing = graph.edges.filter(e => e.source.includes(":step:0"));
        expect(outgoing).toHaveLength(1);
        expect(outgoing[0].outcome).toBe("DEFAULT");
        expect(outgoing[0].label).toBe("Continue");
        const reject = graph.edges.find(e => e.outcome === "REJECT");
        expect(reject).toBeUndefined();
    });
});

// ── two outcomes sharing one target ──────────────────────────────────────────

describe("buildDefinitionGraph: two outcomes sharing one target", () =>
{
    it("preserves both edges even when both point to the same step", () =>
    {
        const graph = buildDefinitionGraph(
            baseState([
                approvalStep({ onApprove: { nextStep: "END" }, onReject: { nextStep: "END" } }),
            ]),
        );
        const approve = graph.edges.find(e => e.outcome === "APPROVE");
        const reject = graph.edges.find(e => e.outcome === "REJECT");
        expect(approve).toBeDefined();
        expect(reject).toBeDefined();
        expect(approve!.target).toBe(reject!.target);
    });
});

// ── backward branch / loop ────────────────────────────────────────────────────

describe("buildDefinitionGraph: backward branch", () =>
{
    it("allows backward edge without an error", () =>
    {
        const graph = buildDefinitionGraph(
            baseState([
                approvalStep({ onApprove: { nextStep: "1" } }),
                approvalStep({ onApprove: { nextStep: "0" }, onReject: { nextStep: "END" } }), // backward to step 0
            ]),
        );
        const backEdge = graph.edges.find(
            e => e.source.includes(":step:1") && e.target.includes(":step:0"),
        );
        expect(backEdge).toBeDefined();
    });
});

// ── invalid targets ───────────────────────────────────────────────────────────

describe("buildDefinitionGraph: invalid targets", () =>
{
    it("produces an INVALID_TARGET node and warning for out-of-range index", () =>
    {
        const graph = buildDefinitionGraph(
            baseState([approvalStep({ onApprove: { nextStep: "99" } })]),
        );
        const invalid = graph.nodes.find(n => n.kind === "INVALID_TARGET");
        expect(invalid).toBeDefined();
        expect(graph.warnings.some(w => w.includes("out of range"))).toBe(true);
    });

    it("produces an INVALID_TARGET node and warning for a malformed target", () =>
    {
        const graph = buildDefinitionGraph(
            baseState([approvalStep({ onApprove: { nextStep: "not-a-number" } })]),
        );
        const invalid = graph.nodes.find(n => n.kind === "INVALID_TARGET");
        expect(invalid).toBeDefined();
        expect(graph.warnings.some(w => w.includes("malformed"))).toBe(true);
    });

    it("produces an INVALID_TARGET node for a negative numeric string", () =>
    {
        // "-1" is not matched by /^\d+$/ so it is treated as malformed.
        const graph = buildDefinitionGraph(
            baseState([approvalStep({ onApprove: { nextStep: "-1" } })]),
        );
        const invalid = graph.nodes.find(n => n.kind === "INVALID_TARGET");
        expect(invalid).toBeDefined();
    });
});

// ── webhook detection ─────────────────────────────────────────────────────────

describe("buildDefinitionGraph: webhook detection", () =>
{
    it("classifies WEBHOOK_DELIVER ACTION step as WEBHOOK graph node, not ACTION", () =>
    {
        const webhookStep: WorkflowStepSpecDraft = {
            type: "ACTION",
            actionHandlerKey: WEBHOOK_DELIVER_HANDLER_KEY,
            assignees: [],
            quorum: { kind: "ANY" },
            addons: [],
        };
        const graph = buildDefinitionGraph(baseState([webhookStep]));
        const node = graph.nodes.find(n => n.kind === "STEP");
        expect(node).toBeDefined();
        expect(node!.stepKind).toBe("WEBHOOK");
    });

    it("classifies future WEBHOOK step type as WEBHOOK graph node", () =>
    {
        const webhookStep: WorkflowStepSpecDraft = {
            type: "WEBHOOK" as never, // future type, not in current enum
            assignees: [],
            quorum: { kind: "ANY" },
            addons: [],
        };
        const graph = buildDefinitionGraph(baseState([webhookStep]));
        const node = graph.nodes.find(n => n.kind === "STEP");
        expect(node!.stepKind).toBe("WEBHOOK");
    });

    it("classifies an ACTION step with a different handler key as ACTION, not WEBHOOK", () =>
    {
        const actionStep: WorkflowStepSpecDraft = {
            type: "ACTION",
            actionHandlerKey: "SOME_OTHER_HANDLER",
            assignees: [],
            quorum: { kind: "ANY" },
            addons: [],
        };
        const graph = buildDefinitionGraph(baseState([actionStep]));
        const node = graph.nodes.find(n => n.kind === "STEP");
        expect(node!.stepKind).toBe("ACTION");
    });
});

// ── render ID namespacing ─────────────────────────────────────────────────────

describe("buildDefinitionGraph: render ID namespacing", () =>
{
    it("does not collide between two graphs with the same step count but different IDs", () =>
    {
        const graph1 = buildDefinitionGraph(baseState([approvalStep()], "def-1"));
        const graph2 = buildDefinitionGraph(baseState([approvalStep()], "def-2"));
        const ids1 = new Set(graph1.nodes.map(n => n.id));
        const ids2 = new Set(graph2.nodes.map(n => n.id));
        const intersection = [...ids1].filter(id => ids2.has(id));
        expect(intersection).toHaveLength(0);
    });
});

// ── step name display ─────────────────────────────────────────────────────────

describe("buildDefinitionGraph: step labels", () =>
{
    it("uses step name when present", () =>
    {
        const graph = buildDefinitionGraph(
            baseState([approvalStep({ name: "My Custom Name" })]),
        );
        const node = graph.nodes.find(n => n.kind === "STEP");
        expect(node!.label).toBe("My Custom Name");
    });

    it("falls back to deterministic label when name is absent", () =>
    {
        const graph = buildDefinitionGraph(baseState([approvalStep()]));
        const node = graph.nodes.find(n => n.kind === "STEP");
        expect(node!.label).toBe("Approval 1");
    });
});

// ── incomplete / partial step ─────────────────────────────────────────────────

describe("buildDefinitionGraph: incomplete step", () =>
{
    it("renders a step node without crashing when most fields are missing", () =>
    {
        const minimalStep: WorkflowStepSpecDraft = {
            type: "APPROVAL",
            assignees: [],
            quorum: { kind: "ANY" },
            addons: [],
        };
        expect(() => buildDefinitionGraph(baseState([minimalStep]))).not.toThrow();
    });
});
