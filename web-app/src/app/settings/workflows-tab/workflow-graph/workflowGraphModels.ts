import {WorkflowStepType} from "../../../models/models.tsx";

export type WorkflowGraphMode = "DEFINITION_PREVIEW" | "INSTANCE_VIEW";

export type WorkflowGraphDirection = "LR" | "TB";

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

export const WEBHOOK_DELIVER_HANDLER_KEY = "WEBHOOK_DELIVER";

export const NODE_WIDTH = 180;
export const NODE_HEIGHT = 88;
export const START_END_WIDTH = 80;
export const START_END_HEIGHT = 40;
export const LAYOUT_RANK_SEP = 170;
export const LAYOUT_NODE_SEP = 90;
export const EDGE_BEND_OFFSET = 40;
