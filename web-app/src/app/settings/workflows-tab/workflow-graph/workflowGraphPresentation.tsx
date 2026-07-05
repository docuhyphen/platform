/**
 * Renderer-specific presentation mapping for the workflow graph.
 *
 * Centralises the step-kind icon and edge visual treatment so nodes, edges,
 * and the legend all share one source of truth. Colour is never the only
 * signal: every state also differs by width, dash pattern, or icon.
 */

import {CSSProperties} from "react";
import {tokens} from "@fluentui/react-components";
import {FluentIcon} from "@fluentui/react-icons";
import {
    WorkflowActionNodeIcon,
    WorkflowApprovalNodeIcon,
    WorkflowConditionNodeIcon,
    WorkflowNotificationNodeIcon,
    WorkflowUnknownStepNodeIcon,
    WorkflowWaitNodeIcon,
    WorkflowWebhookNodeIcon,
} from "../../../components/IconBundles.tsx";
import {WorkflowGraphEdgeState, WorkflowGraphStepKind} from "./workflowGraphModels.ts";

const STEP_KIND_ICONS: Record<WorkflowGraphStepKind, FluentIcon> = {
    APPROVAL: WorkflowApprovalNodeIcon,
    NOTIFICATION: WorkflowNotificationNodeIcon,
    CONDITION: WorkflowConditionNodeIcon,
    ACTION: WorkflowActionNodeIcon,
    WAIT_FOR_COUNTERPARTY_CLEARANCE: WorkflowWaitNodeIcon,
    WEBHOOK: WorkflowWebhookNodeIcon,
    UNKNOWN: WorkflowUnknownStepNodeIcon,
};

export function stepKindIcon(kind: WorkflowGraphStepKind | undefined): FluentIcon
{
    if (kind === undefined) return WorkflowUnknownStepNodeIcon;
    return STEP_KIND_ICONS[kind] ?? WorkflowUnknownStepNodeIcon;
}

const EDGE_STYLES: Record<WorkflowGraphEdgeState, CSSProperties> = {
    DEFAULT:       { stroke: tokens.colorNeutralStroke1, strokeWidth: 1.5 },
    // Same thickness as the other edge states; only the colour communicates
    // that this connector was the one actually traversed.
    TRAVERSED:     { stroke: tokens.colorStatusSuccessBorder1, strokeWidth: 1.5 },
    NOT_TRAVERSED: { stroke: tokens.colorNeutralStroke2, strokeWidth: 1.5, strokeDasharray: "2 3", opacity: 0.6 },
    INACTIVE:      { stroke: tokens.colorNeutralStroke3, strokeWidth: 1, opacity: 0.45 },
    TERMINAL:      { stroke: tokens.colorNeutralStroke1, strokeWidth: 2.5 },
    INVALID:       { stroke: tokens.colorStatusDangerBorder1, strokeWidth: 1.5, strokeDasharray: "6 4" },
};

export function edgeVisualStyle(state: WorkflowGraphEdgeState): CSSProperties
{
    return EDGE_STYLES[state] ?? EDGE_STYLES.DEFAULT;
}

export function edgeMarkerColor(state: WorkflowGraphEdgeState): string
{
    return (EDGE_STYLES[state]?.stroke as string) ?? tokens.colorNeutralStroke1;
}
