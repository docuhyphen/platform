/**
 * Shared formatting helpers for the workflow graph.
 * Must not import React.
 */

import {WorkflowGraphStepKind, WorkflowGraphNodeState, WorkflowGraphEdgeState} from "./workflowGraphModels.ts";

// ── Step kind display labels ──────────────────────────────────────────────────

const STEP_KIND_LABELS: Record<WorkflowGraphStepKind, string> = {
    APPROVAL: "Approval",
    NOTIFICATION: "Notification",
    CONDITION: "Condition",
    ACTION: "Action",
    WAIT_FOR_COUNTERPARTY_CLEARANCE: "Wait for Counterparty",
    WEBHOOK: "Webhook",
    UNKNOWN: "Step",
};

export function stepKindLabel(kind: WorkflowGraphStepKind): string
{
    return STEP_KIND_LABELS[kind] ?? "Step";
}

// ── Node state display labels and accessibility ───────────────────────────────

export interface NodeStatePresentation
{
    label: string;
    accessibilitySuffix: string;
}

const NODE_STATE_LABELS: Record<WorkflowGraphNodeState, NodeStatePresentation> = {
    DEFINITION:              { label: "",                       accessibilitySuffix: "" },
    NOT_REACHED:             { label: "Not reached",            accessibilitySuffix: "not reached" },
    ACTIVE:                  { label: "In progress",            accessibilitySuffix: "in progress" },
    AWAITING_COUNTERPARTY:   { label: "Waiting for counterparty", accessibilitySuffix: "awaiting counterparty" },
    COMPLETED:               { label: "Completed",              accessibilitySuffix: "completed" },
    SKIPPED:                 { label: "Skipped",                accessibilitySuffix: "skipped" },
    REJECTED:                { label: "Rejected",               accessibilitySuffix: "rejected" },
    ESCALATED:               { label: "Escalated",              accessibilitySuffix: "escalated" },
    CANCELLED:               { label: "Cancelled",              accessibilitySuffix: "cancelled" },
    FAILED:                  { label: "Failed",                 accessibilitySuffix: "failed" },
    PAUSED:                  { label: "Paused",                 accessibilitySuffix: "paused" },
    UNKNOWN:                 { label: "Unknown status",         accessibilitySuffix: "unknown status" },
};

export function nodeStatePresentation(state: WorkflowGraphNodeState): NodeStatePresentation
{
    return NODE_STATE_LABELS[state] ?? NODE_STATE_LABELS.UNKNOWN;
}

// ── Edge state labels ─────────────────────────────────────────────────────────

export interface EdgeStatePresentation
{
    accessibilityLabel: string;
}

const EDGE_STATE_LABELS: Record<WorkflowGraphEdgeState, EdgeStatePresentation> = {
    DEFAULT:       { accessibilityLabel: "connection" },
    TRAVERSED:     { accessibilityLabel: "traversed path" },
    NOT_TRAVERSED: { accessibilityLabel: "not traversed path" },
    INACTIVE:      { accessibilityLabel: "inactive path" },
    TERMINAL:      { accessibilityLabel: "final path" },
    INVALID:       { accessibilityLabel: "invalid connection" },
};

export function edgeStatePresentation(state: WorkflowGraphEdgeState): EdgeStatePresentation
{
    return EDGE_STATE_LABELS[state] ?? EDGE_STATE_LABELS.DEFAULT;
}

// ── Outcome labels ────────────────────────────────────────────────────────────

const OUTCOME_LABELS: Record<string, string> = {
    DEFAULT:  "",
    APPROVE:  "Approve",
    REJECT:   "Reject",
    TRUE:     "True",
    FALSE:    "False",
};

export function outcomeLabel(outcome: string): string
{
    return OUTCOME_LABELS[outcome] ?? outcome;
}

// ── Node accessibility label ──────────────────────────────────────────────────

export function buildNodeAccessibilityLabel(
    stepIndex: number | undefined,
    label: string,
    state: WorkflowGraphNodeState,
): string
{
    const stepPart = stepIndex !== undefined ? `Step ${stepIndex + 1}: ` : "";
    const statePart = nodeStatePresentation(state).accessibilitySuffix;
    if (statePart)
    {
        return `${statePart}: ${stepPart}${label}`;
    }
    return `${stepPart}${label}`;
}

// ── Numeric next-step parsing ─────────────────────────────────────────────────

/**
 * Parse a `StepOutcomeSpec.nextStep` value.
 * Returns `"END"` if the value is the string `"END"`.
 * Returns a non-negative integer index if the value is a numeric string.
 * Returns `null` if the value is null, undefined, empty, or otherwise invalid.
 */
export function parseNextStep(raw: string | null | undefined): "END" | number | null
{
    if (raw === null || raw === undefined || raw === "")
    {
        return null;
    }
    if (raw === "END")
    {
        return "END";
    }
    // Must be a non-negative integer string. Do not use permissive parsing.
    if (/^\d+$/.test(raw))
    {
        return parseInt(raw, 10);
    }
    return null;
}
