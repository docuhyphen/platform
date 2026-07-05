/**
 * Transforms a `WorkflowDesignerState` into a `WorkflowGraph` for the
 * definition-preview mode.
 *
 * Rules (per plan Phase 1 Definition Adapter Rules):
 * - One Start node and one End node always present.
 * - Empty workflow: Start connected directly to End.
 * - Step render IDs namespaced as `def:{definitionId}:step:{index}`.
 * - Webhook detected from both `actionHandlerKey == WEBHOOK_DELIVER` and
 *   a future `WEBHOOK` step type (Decision 9).
 * - `WAIT_FOR_COUNTERPARTY_CLEARANCE` produces one neutral `Continue` edge via
 *   `onApprove` and no rejection edge (Decision 21).
 * - Invalid targets produce `INVALID_TARGET` nodes and are reported as warnings.
 * - Missing outcomes default to END (matches engine behaviour).
 * - Applicability is NOT rendered as a step node.
 * - All graph warnings are structural and informational only.
 */

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

// ── ID helpers ─────────────────────────────────────────────────────────────────
//
// Render IDs are namespaced by an `idPrefix` (Decision 18) so the definition
// preview (`def:{definitionId}`) and the frozen instance view (`inst:{instanceId}`)
// never collide, while identical topology still produces identical layout.

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

// ── Step kind classification ───────────────────────────────────────────────────

function classifyStepKind(step: WorkflowStepSpecDraft): WorkflowGraphStepKind
{
    if (step.type === "WEBHOOK") return "WEBHOOK";
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

// ── Step details ──────────────────────────────────────────────────────────────

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

// ── Main adapter ──────────────────────────────────────────────────────────────

export function buildDefinitionGraph(
    state: WorkflowDesignerState,
): WorkflowGraph
{
    const definitionId = state.id ?? "new";
    return buildBaseGraph(state.steps, `def:${definitionId}`);
}

/**
 * Builds the shared frozen topology (Start, step nodes, branch edges, End) from a
 * list of steps under the given render-ID prefix. This is the single source of
 * topology interpretation reused by both the definition preview and the frozen
 * instance view, so equal step lists always yield equal graph shape and layout.
 * All nodes and edges are emitted in the neutral `DEFINITION` / structural state;
 * runtime overlays are applied by the instance adapter afterwards.
 */
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

    // Start node.
    nodes.push({
        id: sId,
        kind: "START",
        label: "Start",
        state: "DEFINITION",
        details: [],
        accessibilityLabel: "Start",
    });

    // End node.
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
        // Empty workflow: Start -> End.
        edges.push({
            id: withPrefix(prefix, "edge:start:end"),
            source: sId,
            target: eId,
            outcome: "DEFAULT",
            state: "DEFAULT",
        });
        return { nodes, edges, warnings };
    }

    // Connect Start to step 0.
    edges.push({
        id: withPrefix(prefix, "edge:start:step:0"),
        source: sId,
        target: stepId(prefix, 0),
        outcome: "DEFAULT",
        state: "DEFAULT",
    });

    // Build step nodes and their outgoing edges.
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
            // Single neutral continuation edge via onApprove; no rejection edge.
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

            // Rejection edge only when explicitly configured.
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
            // Unknown step type: fall back to onApprove.
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

    // Absent / empty outcome: engine defaults to END. No warning.
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
            // Out-of-range index.
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

    // Non-null, non-END, non-numeric: malformed target.
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
