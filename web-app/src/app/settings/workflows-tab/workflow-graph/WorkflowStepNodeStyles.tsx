import {makeStyles, tokens} from "@fluentui/react-components";
import {WorkflowGraphNodeState} from "./workflowGraphModels.ts";

/**
 * Fixed node dimensions. These px values MUST stay in sync with
 * NODE_WIDTH / NODE_HEIGHT / START_END_WIDTH / START_END_HEIGHT in
 * workflowGraphModels.ts so runtime state can never shift layout (Decision 12).
 */
export const useWorkflowStepNodeStyles = makeStyles({
    stepNode: {
        boxSizing: "border-box",
        width: "180px",
        height: "88px",
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        padding: "10px 10px",
        borderRadius: tokens.borderRadiusMedium,
        border: `2px solid ${tokens.colorNeutralStroke1}`,
        backgroundColor: tokens.colorNeutralBackground1,
        overflow: "hidden",
    },

    headerRow: {
        display: "flex",
        alignItems: "center",
        gap: "6px",
        minHeight: "16px",
    },

    kindLabel: {
        fontSize: tokens.fontSizeBase200,
        lineHeight: tokens.lineHeightBase200,
        color: tokens.colorNeutralForeground3,
        textTransform: "uppercase",
        letterSpacing: "0.03em",
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },

    title: {
        fontSize: tokens.fontSizeBase300,
        lineHeight: tokens.lineHeightBase300,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground1,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        // Room for descenders ("p", "g", "y") that a line-height tight to the
        // font size would otherwise clip against the node's overflow:hidden.
        paddingBottom: "2px",
    },

    details: {
        fontSize: tokens.fontSizeBase200,
        lineHeight: tokens.lineHeightBase200,
        color: tokens.colorNeutralForeground3,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },

    stateBadge: {
        fontSize: tokens.fontSizeBase100,
        lineHeight: tokens.lineHeightBase100,
        fontWeight: tokens.fontWeightSemibold,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },

    terminalNode: {
        boxSizing: "border-box",
        width: "80px",
        height: "40px",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        borderRadius: tokens.borderRadiusCircular,
        border: `2px solid ${tokens.colorNeutralStroke1}`,
        backgroundColor: tokens.colorNeutralBackground3,
        fontSize: tokens.fontSizeBase300,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground2,
    },

    invalidNode: {
        boxSizing: "border-box",
        width: "180px",
        height: "88px",
        display: "flex",
        alignItems: "center",
        gap: "6px",
        padding: "8px 10px",
        borderRadius: tokens.borderRadiusMedium,
        border: `2px dashed ${tokens.colorStatusDangerBorder1}`,
        backgroundColor: tokens.colorStatusDangerBackground1,
        color: tokens.colorStatusDangerForeground1,
        fontSize: tokens.fontSizeBase200,
        overflow: "hidden",
    },

    handle: {
        width: "8px",
        height: "8px",
        backgroundColor: tokens.colorNeutralStroke1,
        border: "none",
    },

    // ── Per-state border treatments (colour is never the only signal) ──────
    stateDefinition:           { borderTopColor: tokens.colorNeutralStroke1, borderRightColor: tokens.colorNeutralStroke1, borderBottomColor: tokens.colorNeutralStroke1, borderLeftColor: tokens.colorNeutralStroke1 },
    stateNotReached:           { borderTopColor: tokens.colorNeutralStroke3, borderRightColor: tokens.colorNeutralStroke3, borderBottomColor: tokens.colorNeutralStroke3, borderLeftColor: tokens.colorNeutralStroke3, opacity: 0.6 },
    stateActive:               { borderTopColor: tokens.colorBrandStroke1, borderRightColor: tokens.colorBrandStroke1, borderBottomColor: tokens.colorBrandStroke1, borderLeftColor: tokens.colorBrandStroke1, borderWidth: "3px" },
    stateAwaiting:             { borderTopColor: tokens.colorStatusWarningBorder1, borderRightColor: tokens.colorStatusWarningBorder1, borderBottomColor: tokens.colorStatusWarningBorder1, borderLeftColor: tokens.colorStatusWarningBorder1, borderStyle: "double", borderWidth: "3px" },
    stateCompleted:            { borderTopColor: tokens.colorStatusSuccessBorder1, borderRightColor: tokens.colorStatusSuccessBorder1, borderBottomColor: tokens.colorStatusSuccessBorder1, borderLeftColor: tokens.colorStatusSuccessBorder1 },
    stateSkipped:              { borderStyle: "dashed", borderTopColor: tokens.colorNeutralStroke2, borderRightColor: tokens.colorNeutralStroke2, borderBottomColor: tokens.colorNeutralStroke2, borderLeftColor: tokens.colorNeutralStroke2, opacity: 0.75 },
    stateRejected:             { borderTopColor: tokens.colorStatusDangerBorder1, borderRightColor: tokens.colorStatusDangerBorder1, borderBottomColor: tokens.colorStatusDangerBorder1, borderLeftColor: tokens.colorStatusDangerBorder1 },
    stateEscalated:            { borderTopColor: tokens.colorStatusWarningBorder1, borderRightColor: tokens.colorStatusWarningBorder1, borderBottomColor: tokens.colorStatusWarningBorder1, borderLeftColor: tokens.colorStatusWarningBorder1 },
    stateCancelled:            { borderStyle: "dotted", borderTopColor: tokens.colorNeutralStroke2, borderRightColor: tokens.colorNeutralStroke2, borderBottomColor: tokens.colorNeutralStroke2, borderLeftColor: tokens.colorNeutralStroke2, opacity: 0.7, fontStyle: "italic" },
    stateFailed:               { borderTopColor: tokens.colorStatusDangerBorder1, borderRightColor: tokens.colorStatusDangerBorder1, borderBottomColor: tokens.colorStatusDangerBorder1, borderLeftColor: tokens.colorStatusDangerBorder1, borderWidth: "3px" },
    statePaused:               { borderTopColor: tokens.colorStatusWarningBorder1, borderRightColor: tokens.colorStatusWarningBorder1, borderBottomColor: tokens.colorStatusWarningBorder1, borderLeftColor: tokens.colorStatusWarningBorder1, borderStyle: "dashed" },
    stateUnknown:              { borderTopColor: tokens.colorNeutralStroke3, borderRightColor: tokens.colorNeutralStroke3, borderBottomColor: tokens.colorNeutralStroke3, borderLeftColor: tokens.colorNeutralStroke3, borderStyle: "dotted" },
});

type StepNodeStyles = ReturnType<typeof useWorkflowStepNodeStyles>;

const STATE_CLASS_KEYS: Record<WorkflowGraphNodeState, keyof StepNodeStyles> = {
    DEFINITION: "stateDefinition",
    NOT_REACHED: "stateNotReached",
    ACTIVE: "stateActive",
    AWAITING_COUNTERPARTY: "stateAwaiting",
    COMPLETED: "stateCompleted",
    SKIPPED: "stateSkipped",
    REJECTED: "stateRejected",
    ESCALATED: "stateEscalated",
    CANCELLED: "stateCancelled",
    FAILED: "stateFailed",
    PAUSED: "statePaused",
    UNKNOWN: "stateUnknown",
};

export function nodeStateClass(styles: StepNodeStyles, state: WorkflowGraphNodeState): string
{
    return styles[STATE_CLASS_KEYS[state] ?? "stateUnknown"];
}
