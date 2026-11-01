import {makeStyles, tokens} from "@fluentui/react-components";
import {WorkflowGraphNodeState} from "../workflowGraphModels.ts";

export const useWorkflowGraphLegendStyles = makeStyles({
    legend: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalL,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalXS}`,
        alignItems: "flex-start",
    },

    group: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: tokens.spacingHorizontalMNudge,
    },

    groupTitle: {
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground2,
        marginRight: tokens.spacingHorizontalXXS,
    },

    item: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        fontSize: tokens.fontSizeBase200,
        color: tokens.colorNeutralForeground2,
    },

    sample: {
        boxSizing: "border-box",
        width: "16px",
        height: "16px",
        borderRadius: tokens.borderRadiusSmall,
        backgroundColor: tokens.colorNeutralBackground1,
    },

    completedIcon: {
        width: "16px",
        height: "16px",
        color: tokens.colorStatusSuccessForeground1,
    },

    sampleDefinition:           { border: `2px solid ${tokens.colorNeutralStroke1}` },
    sampleNotReached:           { border: `2px solid ${tokens.colorNeutralStroke3}`, opacity: 0.6 },
    sampleActive:               { border: `3px solid ${tokens.colorBrandStroke1}` },
    sampleAwaiting:             { border: `3px double ${tokens.colorStatusWarningBorder1}` },
    sampleCompleted:            { border: `2px solid ${tokens.colorStatusSuccessBorder1}` },
    sampleSkipped:              { border: `2px dashed ${tokens.colorNeutralStroke2}`, opacity: 0.75 },
    sampleRejected:             { border: `2px solid ${tokens.colorStatusDangerBorder1}` },
    sampleEscalated:            { border: `2px solid ${tokens.colorStatusWarningBorder1}` },
    sampleCancelled:            { border: `2px dotted ${tokens.colorNeutralStroke2}`, opacity: 0.7 },
    sampleFailed:               { border: `3px solid ${tokens.colorStatusDangerBorder1}` },
    samplePaused:               { border: `2px dashed ${tokens.colorStatusWarningBorder1}` },
    sampleUnknown:              { border: `2px dotted ${tokens.colorNeutralStroke3}` },
});

type LegendStyles = ReturnType<typeof useWorkflowGraphLegendStyles>;

const SAMPLE_STATE_CLASS_KEYS: Record<WorkflowGraphNodeState, keyof LegendStyles> = {
    DEFINITION: "sampleDefinition",
    NOT_REACHED: "sampleNotReached",
    ACTIVE: "sampleActive",
    AWAITING_COUNTERPARTY: "sampleAwaiting",
    COMPLETED: "sampleCompleted",
    SKIPPED: "sampleSkipped",
    REJECTED: "sampleRejected",
    ESCALATED: "sampleEscalated",
    CANCELLED: "sampleCancelled",
    FAILED: "sampleFailed",
    PAUSED: "samplePaused",
    UNKNOWN: "sampleUnknown",
};

export function legendSampleStateClass(styles: LegendStyles, state: WorkflowGraphNodeState): string
{
    return styles[SAMPLE_STATE_CLASS_KEYS[state] ?? "sampleUnknown"];
}
