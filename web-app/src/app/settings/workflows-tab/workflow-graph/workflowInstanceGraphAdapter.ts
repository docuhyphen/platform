
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

function decodeSnapshotSteps(snapshotJson: string): WorkflowStepSpecDraft[]
{
    const parsed = JSON.parse(snapshotJson || '{"steps":[]}');
    const steps = parsed?.steps;
    return Array.isArray(steps) ? (steps as WorkflowStepSpecDraft[]) : [];
}

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
    if (edge.state === "INVALID") return edge;

    const key = edgeSourceKey(edge, nodeById);
    const traversed = key !== null && traversedKeys.has(key);
    return {...edge, state: traversed ? "TRAVERSED" : "NOT_TRAVERSED"};
}
