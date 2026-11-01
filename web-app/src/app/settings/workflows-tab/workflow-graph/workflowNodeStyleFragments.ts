import {GriffelStyle, shorthands, tokens} from "@fluentui/react-components";
import {WorkflowGraphNodeState} from "./workflowGraphModels.ts";

export const workflowNodeHandleStyle: GriffelStyle = {
    width: "8px",
    height: "8px",
    backgroundColor: tokens.colorNeutralStroke1,
    border: "none",
};

const borderColor = (color: string): GriffelStyle => ({
    borderTopColor: color,
    borderRightColor: color,
    borderBottomColor: color,
    borderLeftColor: color,
});

export const workflowNodeStateStyles = {
    stateDefinition: borderColor(tokens.colorNeutralStroke1),
    stateNotReached: {...borderColor(tokens.colorNeutralStroke3), opacity: 0.6},
    stateActive: {...borderColor(tokens.colorBrandStroke1), ...shorthands.borderWidth(tokens.strokeWidthThicker)},
    stateAwaiting: {
        ...borderColor(tokens.colorStatusWarningBorder1),
        ...shorthands.borderStyle("double"),
        ...shorthands.borderWidth(tokens.strokeWidthThicker),
    },
    stateCompleted: borderColor(tokens.colorStatusSuccessBorder1),
    stateSkipped: {...shorthands.borderStyle("dashed"), ...borderColor(tokens.colorNeutralStroke2), opacity: 0.75},
    stateRejected: borderColor(tokens.colorStatusDangerBorder1),
    stateEscalated: borderColor(tokens.colorStatusWarningBorder1),
    stateCancelled: {
        ...shorthands.borderStyle("dotted"),
        ...borderColor(tokens.colorNeutralStroke2),
        opacity: 0.7,
        fontStyle: "italic",
    },
    stateFailed: {...borderColor(tokens.colorStatusDangerBorder1), ...shorthands.borderWidth(tokens.strokeWidthThicker)},
    statePaused: {...borderColor(tokens.colorStatusWarningBorder1), ...shorthands.borderStyle("dashed")},
    stateUnknown: {...borderColor(tokens.colorNeutralStroke3), ...shorthands.borderStyle("dotted")},
} satisfies Record<string, GriffelStyle>;

export type WorkflowNodeStateStyleKey = keyof typeof workflowNodeStateStyles;

const STATE_CLASS_KEYS: Record<WorkflowGraphNodeState, WorkflowNodeStateStyleKey> = {
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

export function nodeStateClass(styles: Record<WorkflowNodeStateStyleKey, string>, state: WorkflowGraphNodeState): string
{
    return styles[STATE_CLASS_KEYS[state] ?? "stateUnknown"];
}
