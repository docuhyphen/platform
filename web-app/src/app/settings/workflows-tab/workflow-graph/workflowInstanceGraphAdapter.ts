/**
 * Transforms a `WorkflowInstanceDetailDto` with its frozen graph snapshot
 * into a `WorkflowGraph` for the instance-view mode.
 *
 * Rules:
 * - Topology and labels come from the frozen `definitionSnapshotJson`, decoded
 *   into the same `{steps}` DSL the builder serializes, then routed through the
 *   SHARED `buildBaseGraph` so equal builder and instance topology receive equal
 *   layout (Decision 2, Decision 3, Decision 18). Topology is never re-derived.
 * - Node runtime state comes only from each step instance's `status`. Steps that
 *   were never instantiated (lazy engine) render as `NOT_REACHED`.
 * - Edge traversal comes ONLY from explicit `transitions`; it is never inferred
 *   from step order, decisions, timestamps, or status.
 * - A frozen `WAIT_FOR_COUNTERPARTY_CLEARANCE` step keeps its single neutral
 *   continuation edge (from `buildBaseGraph`) and surfaces its parked
 *   `AWAITING_COUNTERPARTY` node state (Decision 21).
 * - Unknown future step types and statuses resolve to safe neutral fallbacks.
 */

import {
    WorkflowInstanceDetailDto,
    WorkflowStepInstanceDto,
    WorkflowStepSpecDraft,
    WorkflowStepTransitionDto,
} from "../../../models/models.tsx";
import {buildBaseGraph} from "./workflowDefinitionGraphAdapter.ts";
import {
    WorkflowGraph,
    WorkflowGraphEdge,
    WorkflowGraphNode,
    WorkflowGraphNodeState,
} from "./workflowGraphModels.ts";
import {buildNodeAccessibilityLabel, nodeStatePresentation} from "./workflowGraphUtils.ts";

// ── Snapshot decoding ──────────────────────────────────────────────────────────

/**
 * Decodes the frozen `definitionSnapshotJson` into the step list. The snapshot is
 * the verbatim `{steps, applicability}` blob the builder serializes, so the same
 * shape decoded here reproduces the builder topology exactly. A malformed snapshot
 * throws so the rendering-error boundary (Decision 16) can fall back to Timeline.
 */
function decodeSnapshotSteps(snapshotJson: string): WorkflowStepSpecDraft[]
{
    const parsed = JSON.parse(snapshotJson || '{"steps":[]}');
    const steps = parsed?.steps;
    return Array.isArray(steps) ? (steps as WorkflowStepSpecDraft[]) : [];
}

// ── Runtime node state mapping ──────────────────────────────────────────────────

/**
 * Maps a step instance status to the graph node-state contract. A step instance
 * only exists once the engine reaches it (lazy creation), so the absence of a
 * step instance is the `NOT_REACHED` signal rather than any inferred status.
 */
function stepNodeState(step: WorkflowStepInstanceDto | undefined): WorkflowGraphNodeState
{
    if (step === undefined) return "NOT_REACHED";
    switch (step.status)
    {
        case "PENDING": return "ACTIVE";
        case "APPROVED":
        case "COMPLETED": return "COMPLETED";
        case "REJECTED": return "REJECTED";
        case "ESCALATED": return "ESCALATED";
        case "SKIPPED": return "SKIPPED";
        case "AWAITING_COUNTERPARTY": return "AWAITING_COUNTERPARTY";
        default: return "UNKNOWN";
    }
}

/**
 * Terminal instance status projected onto the End node. A still-running instance
 * has not reached End.
 */
function endNodeState(instanceStatus: string): WorkflowGraphNodeState
{
    switch (instanceStatus)
    {
        case "COMPLETED": return "COMPLETED";
        case "REJECTED": return "REJECTED";
        case "CANCELLED": return "CANCELLED";
        case "ESCALATED": return "ESCALATED";
        default: return "NOT_REACHED";
    }
}

// ── Runtime detail rows ─────────────────────────────────────────────────────────

function formatDate(iso: string | undefined): string | undefined
{
    if (!iso) return undefined;
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return undefined;
    return date.toLocaleDateString();
}

function plural(count: number, noun: string): string
{
    return `${count} ${noun}${count !== 1 ? "s" : ""}`;
}

function buildStepDetails(
    stepIndex: number,
    stepCount: number,
    state: WorkflowGraphNodeState,
    step: WorkflowStepInstanceDto | undefined,
): string[]
{
    const details: string[] = [];
    details.push(`Step ${stepIndex + 1} of ${stepCount}`);
    const statusLabel = nodeStatePresentation(state).label;
    if (statusLabel) details.push(statusLabel);
    if (step)
    {
        if (step.assignees.length > 0) details.push(plural(step.assignees.length, "assignee"));
        if (step.decisions.length > 0) details.push(plural(step.decisions.length, "decision"));
        const completed = formatDate(step.completedAt);
        if (completed) details.push(`Completed ${completed}`);
    }
    return details;
}

// ── Traversal overlay ───────────────────────────────────────────────────────────

/**
 * A transition uniquely identifies a definition edge by its source step index
 * (null = the START edge) plus its outcome, matching exactly one edge produced by
 * `buildBaseGraph` for that source. `toStepIndex` is not part of the key because
 * the frozen topology already fixes the target for each source/outcome pair.
 */
function transitionKey(fromStepIndex: number | null, outcome: string): string
{
    const from = fromStepIndex === null ? "START" : String(fromStepIndex);
    return `${from}:${outcome}`;
}

function edgeSourceKey(
    edge: WorkflowGraphEdge,
    nodeById: Map<string, WorkflowGraphNode>,
): string | null
{
    const source = nodeById.get(edge.source);
    if (source === undefined) return null;
    if (source.kind === "START") return transitionKey(null, edge.outcome);
    if (source.stepIndex === undefined) return null;
    return transitionKey(source.stepIndex, edge.outcome);
}

// ── Main adapter ────────────────────────────────────────────────────────────────

export function buildInstanceGraph(instance: WorkflowInstanceDetailDto): WorkflowGraph
{
    const steps = decodeSnapshotSteps(instance.definitionSnapshotJson);
    const base = buildBaseGraph(steps, `inst:${instance.id}`);

    const stepByIndex = new Map<number, WorkflowStepInstanceDto>();
    for (const step of instance.steps)
    {
        stepByIndex.set(step.stepIndex, step);
    }

    const nodes = base.nodes.map(node => overlayNode(node, stepByIndex, steps.length, instance.status));

    const nodeById = new Map<string, WorkflowGraphNode>();
    for (const node of base.nodes)
    {
        nodeById.set(node.id, node);
    }

    const traversedKeys = new Set<string>();
    for (const transition of instance.transitions as WorkflowStepTransitionDto[])
    {
        traversedKeys.add(transitionKey(transition.fromStepIndex, transition.outcome));
    }

    const edges = base.edges.map(edge => overlayEdge(edge, traversedKeys, nodeById));

    return {nodes, edges, warnings: base.warnings};
}

function overlayNode(
    node: WorkflowGraphNode,
    stepByIndex: Map<number, WorkflowStepInstanceDto>,
    stepCount: number,
    instanceStatus: string,
): WorkflowGraphNode
{
    if (node.kind === "START")
    {
        const state: WorkflowGraphNodeState = "COMPLETED";
        return {...node, state, accessibilityLabel: buildNodeAccessibilityLabel(undefined, node.label, state)};
    }
    if (node.kind === "END")
    {
        const state = endNodeState(instanceStatus);
        return {...node, state, accessibilityLabel: buildNodeAccessibilityLabel(undefined, node.label, state)};
    }
    if (node.kind !== "STEP" || node.stepIndex === undefined)
    {
        // INVALID_TARGET or any non-step node: keep structural presentation.
        return node;
    }

    const step = stepByIndex.get(node.stepIndex);
    const state = stepNodeState(step);
    return {
        ...node,
        state,
        sourceStatus: step?.status,
        details: buildStepDetails(node.stepIndex, stepCount, state, step),
        accessibilityLabel: buildNodeAccessibilityLabel(node.stepIndex, node.label, state),
    };
}

function overlayEdge(
    edge: WorkflowGraphEdge,
    traversedKeys: Set<string>,
    nodeById: Map<string, WorkflowGraphNode>,
): WorkflowGraphEdge
{
    // Structurally invalid edges keep their invalid treatment.
    if (edge.state === "INVALID") return edge;

    const key = edgeSourceKey(edge, nodeById);
    const traversed = key !== null && traversedKeys.has(key);
    return {...edge, state: traversed ? "TRAVERSED" : "NOT_TRAVERSED"};
}
