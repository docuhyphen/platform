import {describe, expect, it} from "vitest";
import {
    WorkflowInstanceDetailDto,
    WorkflowStepInstanceDto,
    WorkflowStepSpecDraft,
    WorkflowStepTransitionDto,
} from "../../../../models/models.tsx";
import {buildInstanceGraph} from "../workflowInstanceGraphAdapter.ts";
import {WEBHOOK_DELIVER_HANDLER_KEY} from "../workflowGraphModels.ts";

// ── helpers ───────────────────────────────────────────────────────────────────

const approvalStep = (overrides: Partial<WorkflowStepSpecDraft> = {}): WorkflowStepSpecDraft => ({
    type: "APPROVAL",
    assignees: [],
    quorum: {kind: "ANY"},
    addons: [],
    ...overrides,
});

const stepInstance = (overrides: Partial<WorkflowStepInstanceDto> = {}): WorkflowStepInstanceDto => ({
    id: "si-0",
    stepIndex: 0,
    stepType: "APPROVAL",
    status: "PENDING",
    assignees: [],
    decisions: [],
    createdAt: "2026-01-01T00:00:00.000Z",
    ...overrides,
});

const instance = (
    steps: WorkflowStepSpecDraft[],
    overrides: Partial<WorkflowInstanceDetailDto> = {},
): WorkflowInstanceDetailDto => ({
    id: "inst-1",
    definitionId: "def-1",
    status: "RUNNING",
    currentStepIndex: 0,
    createdAt: "2026-01-01T00:00:00.000Z",
    steps: [],
    definitionVersion: 1,
    definitionSnapshotJson: JSON.stringify({steps}),
    transitions: [],
    ...overrides,
});

// ── topology reuse ──────────────────────────────────────────────────────────────

describe("buildInstanceGraph: topology", () =>
{
    it("reuses the builder topology from the frozen snapshot", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({onApprove: {nextStep: "1"}}),
            approvalStep({onApprove: {nextStep: "END"}}),
        ]));

        expect(graph.nodes.some(n => n.kind === "START")).toBe(true);
        expect(graph.nodes.some(n => n.kind === "END")).toBe(true);
        expect(graph.nodes.filter(n => n.kind === "STEP")).toHaveLength(2);
        // Instance render IDs are namespaced by instance id.
        expect(graph.nodes.every(n => n.id.startsWith("inst:inst-1:"))).toBe(true);
    });

    it("preserves step names from the frozen snapshot", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({name: "Manager sign-off", onApprove: {nextStep: "END"}}),
        ]));
        const step = graph.nodes.find(n => n.kind === "STEP");
        expect(step?.label).toBe("Manager sign-off");
    });

    it("throws on a malformed snapshot so the error boundary can fall back", () =>
    {
        expect(() => buildInstanceGraph(instance([], {definitionSnapshotJson: "{not-json"})))
            .toThrow();
    });
});

// ── node state overlay ──────────────────────────────────────────────────────────

describe("buildInstanceGraph: node state", () =>
{
    it("marks a never-instantiated step as NOT_REACHED", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({onApprove: {nextStep: "END"}}),
        ], {steps: []}));
        const step = graph.nodes.find(n => n.kind === "STEP");
        expect(step?.state).toBe("NOT_REACHED");
    });

    it("marks a PENDING step as ACTIVE", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({onApprove: {nextStep: "END"}}),
        ], {steps: [stepInstance({status: "PENDING"})]}));
        const step = graph.nodes.find(n => n.kind === "STEP");
        expect(step?.state).toBe("ACTIVE");
    });

    it("maps APPROVED and COMPLETED to COMPLETED", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({onApprove: {nextStep: "1"}}),
            approvalStep({onApprove: {nextStep: "END"}}),
        ], {
            steps: [
                stepInstance({id: "si-0", stepIndex: 0, status: "APPROVED"}),
                stepInstance({id: "si-1", stepIndex: 1, status: "COMPLETED"}),
            ],
        }));
        const states = graph.nodes.filter(n => n.kind === "STEP").map(n => n.state);
        expect(states).toEqual(["COMPLETED", "COMPLETED"]);
    });

    it("surfaces AWAITING_COUNTERPARTY for a parked wait step", () =>
    {
        const graph = buildInstanceGraph(instance([
            {...approvalStep(), type: "WAIT_FOR_COUNTERPARTY_CLEARANCE", onApprove: {nextStep: "END"}},
        ], {steps: [stepInstance({stepType: "WAIT_FOR_COUNTERPARTY_CLEARANCE", status: "AWAITING_COUNTERPARTY"})]}));
        const step = graph.nodes.find(n => n.kind === "STEP");
        expect(step?.state).toBe("AWAITING_COUNTERPARTY");
    });

    it("maps an unknown status to UNKNOWN without throwing", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({onApprove: {nextStep: "END"}}),
        ], {steps: [stepInstance({status: "SOME_FUTURE_STATUS"})]}));
        const step = graph.nodes.find(n => n.kind === "STEP");
        expect(step?.state).toBe("UNKNOWN");
    });

    it("projects a terminal instance status onto the End node", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({onApprove: {nextStep: "END"}}),
        ], {status: "REJECTED", steps: [stepInstance({status: "REJECTED"})]}));
        const end = graph.nodes.find(n => n.kind === "END");
        expect(end?.state).toBe("REJECTED");
    });

    it("leaves the End node NOT_REACHED while the instance is running", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({onApprove: {nextStep: "END"}}),
        ], {status: "RUNNING", steps: [stepInstance({status: "PENDING"})]}));
        const end = graph.nodes.find(n => n.kind === "END");
        expect(end?.state).toBe("NOT_REACHED");
    });
});

// ── edge traversal overlay ──────────────────────────────────────────────────────

describe("buildInstanceGraph: edge traversal", () =>
{
    const transition = (
        fromStepIndex: number | null,
        outcome: WorkflowStepTransitionDto["outcome"],
    ): WorkflowStepTransitionDto => ({fromStepIndex, toStepIndex: null, outcome});

    it("marks the START edge TRAVERSED from an explicit transition", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({onApprove: {nextStep: "END"}}),
        ], {
            transitions: [transition(null, "DEFAULT")],
            steps: [stepInstance({status: "PENDING"})],
        }));
        const startEdge = graph.edges.find(e => e.source.includes(":start"));
        expect(startEdge?.state).toBe("TRAVERSED");
    });

    it("marks configured but untaken branches NOT_TRAVERSED (not failed)", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({onApprove: {nextStep: "END"}, onReject: {nextStep: "END"}}),
        ], {
            transitions: [transition(null, "DEFAULT"), transition(0, "APPROVE")],
            steps: [stepInstance({status: "APPROVED"})],
        }));

        const approveEdge = graph.edges.find(e => e.source.includes(":step:0") && e.outcome === "APPROVE");
        const rejectEdge = graph.edges.find(e => e.source.includes(":step:0") && e.outcome === "REJECT");
        expect(approveEdge?.state).toBe("TRAVERSED");
        expect(rejectEdge?.state).toBe("NOT_TRAVERSED");
    });

    it("never infers traversal without a transition record", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({onApprove: {nextStep: "END"}}),
        ], {transitions: [], steps: [stepInstance({status: "APPROVED"})]}));
        expect(graph.edges.every(e => e.state !== "TRAVERSED")).toBe(true);
    });

    it("matches condition TRUE/FALSE outcomes explicitly", () =>
    {
        const graph = buildInstanceGraph(instance([
            {...approvalStep(), type: "CONDITION", onTrue: {nextStep: "END"}, onFalse: {nextStep: "END"}},
        ], {
            transitions: [transition(null, "DEFAULT"), transition(0, "TRUE")],
            steps: [stepInstance({stepType: "CONDITION", status: "COMPLETED"})],
        }));
        const trueEdge = graph.edges.find(e => e.source.includes(":step:0") && e.outcome === "TRUE");
        const falseEdge = graph.edges.find(e => e.source.includes(":step:0") && e.outcome === "FALSE");
        expect(trueEdge?.state).toBe("TRAVERSED");
        expect(falseEdge?.state).toBe("NOT_TRAVERSED");
    });
});

// ── webhook detection ───────────────────────────────────────────────────────────

describe("buildInstanceGraph: webhook", () =>
{
    it("renders a WEBHOOK_DELIVER action step as a distinct Webhook node", () =>
    {
        const graph = buildInstanceGraph(instance([
            approvalStep({type: "ACTION", actionHandlerKey: WEBHOOK_DELIVER_HANDLER_KEY, onApprove: {nextStep: "END"}}),
        ], {steps: [stepInstance({stepType: "ACTION", status: "COMPLETED"})]}));
        const step = graph.nodes.find(n => n.kind === "STEP");
        expect(step?.stepKind).toBe("WEBHOOK");
    });
});
