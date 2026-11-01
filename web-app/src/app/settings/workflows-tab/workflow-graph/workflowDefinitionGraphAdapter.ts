
import {WorkflowDesignerState, WorkflowStepSpecDraft} from "../../../models/models.tsx";
import {
    WEBHOOK_DELIVER_HANDLER_KEY,
    WorkflowGraph,
    WorkflowGraphEdge,
    WorkflowGraphNode,
    WorkflowGraphOutcome,
    WorkflowGraphStepKind,
} from "./workflowGraphModels.ts";
import {buildNodeAccessibilityLabel, parseNextStep, stepKindLabel} from "./workflowGraphUtils.ts";

function withPrefix(prefix: string, suffix: string): string
{
    return `${prefix}:${suffix}`;
}

function stepId(prefix: string, index: number): string
{
    return withPrefix(prefix, `step:${index}`);
}

function startId(prefix: string): string
{
    return withPrefix(prefix, "start");
}

function endId(prefix: string): string
{
    return withPrefix(prefix, "end");
}

function invalidTargetId(prefix: string, suffix: string): string
{
    return withPrefix(prefix, `invalid:${suffix}`);
}

function classifyStepKind(step: WorkflowStepSpecDraft): WorkflowGraphStepKind
{
    const storedType: string = step.type;
    if (storedType === "WEBHOOK") return "WEBHOOK";
    if (step.type === "ACTION" && step.actionHandlerKey === WEBHOOK_DELIVER_HANDLER_KEY)
    {
        return "WEBHOOK";
    }
    switch (step.type)
    {
        case "APPROVAL": return "APPROVAL";
        case "NOTIFICATION": return "NOTIFICATION";
        case "CONDITION": return "CONDITION";
        case "ACTION": return "ACTION";
        case "WAIT_FOR_COUNTERPARTY_CLEARANCE": return "WAIT_FOR_COUNTERPARTY_CLEARANCE";
        default: return "UNKNOWN";
    }
}

function buildStepDetails(
    step: WorkflowStepSpecDraft,
    index: number,
    stepCount: number,
): string[]
{
    const details: string[] = [];
    const displayNum = `Step ${index + 1} of ${stepCount}`;
    details.push(displayNum);
    if (step.assignees.length > 0)
    {
        details.push(`${step.assignees.length} assignee${step.assignees.length !== 1 ? "s" : ""}`);
    }
    if (step.quorum)
    {
        details.push(`Quorum: ${step.quorum.kind}${step.quorum.kind === "N_OF_M" && step.quorum.n !== undefined ? ` (${step.quorum.n})` : ""}`);
    }
    if (step.slaMinutes !== undefined)
    {
        details.push(`SLA: ${step.slaMinutes} min`);
    }
    return details;
}

export function buildDefinitionGraph(
    state: WorkflowDesignerState,
): WorkflowGraph
{
    const definitionId = state.id ?? "new";
    return buildBaseGraph(state.steps, `def:${definitionId}`);
}

export function buildBaseGraph(
    steps: WorkflowStepSpecDraft[],
    prefix: string,
): WorkflowGraph
{
    const warnings: string[] = [];
    const nodes: WorkflowGraphNode[] = [];
    const edges: WorkflowGraphEdge[] = [];

    const sId = startId(prefix);
    const eId = endId(prefix);

    nodes.push({
        id: sId,
        kind: "START",
        label: "Start",
        state: "DEFINITION",
        details: [],
        accessibilityLabel: "Start",
    });

    nodes.push({
        id: eId,
        kind: "END",
        label: "End",
        state: "DEFINITION",
        details: [],
        accessibilityLabel: "End",
    });

    if (steps.length === 0)
    {
        edges.push({
            id: withPrefix(prefix, "edge:start:end"),
            source: sId,
            target: eId,
            outcome: "DEFAULT",
            state: "DEFAULT",
        });
        return { nodes, edges, warnings };
    }

    edges.push({
        id: withPrefix(prefix, "edge:start:step:0"),
        source: sId,
        target: stepId(prefix, 0),
        outcome: "DEFAULT",
        state: "DEFAULT",
    });

    for (let i = 0; i < steps.length; i++)
    {
        const step = steps[i];
        const kind = classifyStepKind(step);
        const label = step.name?.trim() || `${stepKindLabel(kind)} ${i + 1}`;

        nodes.push({
            id: stepId(prefix, i),
            kind: "STEP",
            stepKind: kind,
            stepIndex: i,
            stepType: step.type,
            label,
            state: "DEFINITION",
            details: buildStepDetails(step, i, steps.length),
            accessibilityLabel: buildNodeAccessibilityLabel(i, label, "DEFINITION"),
        });

        if (step.type === "WAIT_FOR_COUNTERPARTY_CLEARANCE")
        {
            const nextRaw = step.onApprove?.nextStep ?? "END";
            addOutgoingEdge(
                edges, nodes, warnings,
                prefix, i, nextRaw,
                "DEFAULT", "Continue", steps.length,
            );
        }
        else if (step.type === "CONDITION")
        {
            const trueRaw = step.onTrue?.nextStep ?? "END";
            const falseRaw = step.onFalse?.nextStep ?? "END";
            addOutgoingEdge(
                edges, nodes, warnings,
                prefix, i, trueRaw,
                "TRUE", "True", steps.length,
            );
            addOutgoingEdge(
                edges, nodes, warnings,
                prefix, i, falseRaw,
                "FALSE", "False", steps.length,
            );
        }
        else if (step.type === "APPROVAL" || kind === "WEBHOOK" || step.type === "ACTION" || step.type === "NOTIFICATION")
        {
            const approveRaw = step.onApprove?.nextStep ?? "END";
            const rejectRaw = step.onReject?.nextStep ?? null;

            const approveLabel = step.type === "APPROVAL" ? "Approve" : undefined;
            addOutgoingEdge(
                edges, nodes, warnings,
                prefix, i, approveRaw,
                "APPROVE", approveLabel, steps.length,
            );

            if (rejectRaw !== null)
            {
                addOutgoingEdge(
                    edges, nodes, warnings,
                    prefix, i, rejectRaw,
                    "REJECT", "Reject", steps.length,
                );
            }
        }
        else
        {
            const nextRaw = step.onApprove?.nextStep ?? "END";
            addOutgoingEdge(
                edges, nodes, warnings,
                prefix, i, nextRaw,
                "DEFAULT", undefined, steps.length,
            );
        }
    }

    return { nodes, edges, warnings };
}

function addOutgoingEdge(
    edges: WorkflowGraphEdge[],
    nodes: WorkflowGraphNode[],
    warnings: string[],
    prefix: string,
    sourceIndex: number,
    nextRaw: string | null | undefined,
    outcome: WorkflowGraphOutcome,
    edgeLabel: string | undefined,
    stepCount: number,
): void
{
    const sId = stepId(prefix, sourceIndex);
    const eId = endId(prefix);
    const outcomeKey = outcome.toLowerCase();

    if (nextRaw === null || nextRaw === undefined || nextRaw === "")
    {
        const edgeId = `${sId}:${outcomeKey}:end:default`;
        edges.push({
            id: edgeId,
            source: sId,
            target: eId,
            outcome,
            label: edgeLabel,
            state: "TERMINAL",
        });
        return;
    }

    const parsed = parseNextStep(nextRaw);

    if (parsed === "END")
    {
        const edgeId = `${sId}:${outcomeKey}:end`;
        edges.push({
            id: edgeId,
            source: sId,
            target: eId,
            outcome,
            label: edgeLabel,
            state: "TERMINAL",
        });
        return;
    }

    if (typeof parsed === "number")
    {
        if (parsed >= 0 && parsed < stepCount)
        {
            const targetId = stepId(prefix, parsed);
            const edgeId = `${sId}:${outcomeKey}:step:${parsed}`;
            edges.push({
                id: edgeId,
                source: sId,
                target: targetId,
                outcome,
                label: edgeLabel,
                state: "DEFAULT",
            });
        }
        else
        {
            const invalidId = invalidTargetId(prefix, `${sourceIndex}:${outcomeKey}`);
            const label = `Invalid target (step ${parsed})`;

            if (!nodes.some(n => n.id === invalidId))
            {
                nodes.push({
                    id: invalidId,
                    kind: "INVALID_TARGET",
                    label,
                    state: "DEFINITION",
                    details: [],
                    accessibilityLabel: label,
                });
            }

            const edgeId = `${sId}:${outcomeKey}:invalid`;
            edges.push({
                id: edgeId,
                source: sId,
                target: invalidId,
                outcome,
                label: edgeLabel,
                state: "INVALID",
            });

            warnings.push(
                `Step ${sourceIndex + 1} has an invalid branch target: step index ${parsed} is out of range.`,
            );
        }
        return;
    }

    const invalidId = invalidTargetId(prefix, `${sourceIndex}:${outcomeKey}:bad`);
    const label = `Invalid target ("${nextRaw}")`;

    if (!nodes.some(n => n.id === invalidId))
    {
        nodes.push({
            id: invalidId,
            kind: "INVALID_TARGET",
            label,
            state: "DEFINITION",
            details: [],
            accessibilityLabel: label,
        });
    }

    const edgeId = `${sId}:${outcomeKey}:invalid:bad`;
    edges.push({
        id: edgeId,
        source: sId,
        target: invalidId,
        outcome,
        label: edgeLabel,
        state: "INVALID",
    });

    warnings.push(
        `Step ${sourceIndex + 1} has a malformed branch target: "${nextRaw}".`,
    );
}
