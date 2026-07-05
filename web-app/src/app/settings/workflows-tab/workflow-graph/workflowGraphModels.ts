import {WorkflowStepType} from "../../../models/models.tsx";

// ── Graph mode ────────────────────────────────────────────────────────────────

export type WorkflowGraphMode = "DEFINITION_PREVIEW" | "INSTANCE_VIEW";

/**
 * Flow direction for the diagram layout: "LR" arranges ranks left to right
 * (used by the workflow builder's wide preview), "TB" arranges them top to
 * bottom (used by the narrower side-by-side Exchange workflow preview).
 */
export type WorkflowGraphDirection = "LR" | "TB";

// ── Node and edge taxonomy ────────────────────────────────────────────────────

export type WorkflowGraphNodeKind = "START" | "STEP" | "END" | "INVALID_TARGET";

export type WorkflowGraphStepKind =
    | "APPROVAL"
    | "NOTIFICATION"
    | "CONDITION"
    | "ACTION"
    | "WAIT_FOR_COUNTERPARTY_CLEARANCE"
    | "WEBHOOK"
    | "UNKNOWN";

export type WorkflowGraphOutcome =
    | "DEFAULT"
    | "APPROVE"
    | "REJECT"
    | "TRUE"
    | "FALSE";

// ── Runtime states ────────────────────────────────────────────────────────────

export type WorkflowGraphNodeState =
    | "DEFINITION"
    | "NOT_REACHED"
    | "ACTIVE"
    | "AWAITING_COUNTERPARTY"
    | "COMPLETED"
    | "SKIPPED"
    | "REJECTED"
    | "ESCALATED"
    | "CANCELLED"
    | "FAILED"
    | "PAUSED"
    | "UNKNOWN";

export type WorkflowGraphEdgeState =
    | "DEFAULT"
    | "TRAVERSED"
    | "NOT_TRAVERSED"
    | "INACTIVE"
    | "TERMINAL"
    | "INVALID";

// ── Core graph types ──────────────────────────────────────────────────────────

export interface WorkflowGraphNode
{
    id: string;
    kind: WorkflowGraphNodeKind;
    stepKind?: WorkflowGraphStepKind;
    label: string;
    stepIndex?: number;
    stepType?: WorkflowStepType | string;
    state: WorkflowGraphNodeState;
    sourceStatus?: string;
    details: string[];
    accessibilityLabel: string;
}

export interface WorkflowGraphEdge
{
    id: string;
    source: string;
    target: string;
    outcome: WorkflowGraphOutcome;
    label?: string;
    state: WorkflowGraphEdgeState;
}

export interface WorkflowGraph
{
    nodes: WorkflowGraphNode[];
    edges: WorkflowGraphEdge[];
    warnings: string[];
}

// ── Layout ────────────────────────────────────────────────────────────────────

export interface WorkflowGraphPosition
{
    x: number;
    y: number;
}

export interface WorkflowGraphLayout
{
    positions: Map<string, WorkflowGraphPosition>;
}

export interface WorkflowGraphLayoutNode
{
    id: string;
    width: number;
    height: number;
}

export interface WorkflowGraphLayoutEdge
{
    id: string;
    source: string;
    target: string;
}

// ── Shared constant ───────────────────────────────────────────────────────────

/**
 * The handler key that identifies a Webhook delivery ACTION step.
 * Centralised here so adapters and tests never scatter the literal.
 */
export const WEBHOOK_DELIVER_HANDLER_KEY = "WEBHOOK_DELIVER";

// ── Node sizing constants (fixed dimensions keep layout stable) ───────────────

export const NODE_WIDTH = 180;
export const NODE_HEIGHT = 88;
export const START_END_WIDTH = 80;
export const START_END_HEIGHT = 40;
// Rank/node separation is generous enough that edges have room to curve well
// clear of neighbouring node borders before reaching their target, and that
// parallel edges converging on/diverging from the same node (e.g. multiple
// Approve/Reject outcomes) don't route through or behind adjacent nodes.
export const LAYOUT_RANK_SEP = 170;
export const LAYOUT_NODE_SEP = 90;
