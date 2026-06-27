import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowInstanceDetailStyles = makeStyles({
    stepTimeline: {
        display: "flex",
        flexDirection: "column",
        gap: "1rem",
        paddingTop: "1rem",
    },

    stepCard: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: "0.875rem",
    },

    stepHeader: {
        display: "flex",
        alignItems: "center",
        gap: "0.5rem",
        marginBottom: "0.5rem",
    },

    decisionList: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        marginTop: "0.5rem",
        paddingLeft: "0.75rem",
        borderLeft: `3px solid ${tokens.colorNeutralStroke2}`,
    },

    decisionRow: {
        display: "flex",
        gap: "0.5rem",
        alignItems: "center",
    },

    metaText: {
        color: tokens.colorNeutralForeground3,
    },

    slaWarning: {
        color: tokens.colorStatusWarningForeground1,
    },

    currentStepHighlight: {
        borderColor: tokens.colorBrandStroke1,
    },

    exchangeName: {
        marginBottom: "0.5rem",
    },

    instanceStatus: {
        marginBottom: "0.75rem",
        color: "var(--colorNeutralForeground3)",
    },
});

