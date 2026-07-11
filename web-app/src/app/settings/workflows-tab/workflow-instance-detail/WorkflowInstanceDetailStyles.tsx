import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowInstanceDetailStyles = makeStyles({
    stepTimeline: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        paddingTop: tokens.spacingVerticalL,
    },

    stepCard: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: tokens.spacingHorizontalM,
    },

    stepHeader: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        marginBottom: tokens.spacingVerticalS,
    },

    decisionList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        marginTop: tokens.spacingVerticalS,
        paddingLeft: tokens.spacingHorizontalM,
        borderLeft: `3px solid ${tokens.colorNeutralStroke2}`,
    },

    decisionRow: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        alignItems: "center",
    },

    metaText: {
        color: tokens.colorNeutralForeground3,
    },
    decisionApproveText: {
        color: tokens.colorStatusSuccessForeground1,
    },
    decisionRejectText: {
        color: tokens.colorStatusDangerForeground1,
    },

    slaWarning: {
        color: tokens.colorStatusWarningForeground1,
    },

    currentStepHighlight: {
        borderTopColor: tokens.colorBrandStroke1,
        borderRightColor: tokens.colorBrandStroke1,
        borderBottomColor: tokens.colorBrandStroke1,
        borderLeftColor: tokens.colorBrandStroke1,
    },

    exchangeName: {
        marginBottom: tokens.spacingVerticalS,
    },

    instanceStatus: {
        marginBottom: tokens.spacingVerticalM,
        color: tokens.colorNeutralForeground3,
    },
});

