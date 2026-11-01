import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowStepDetailStyles = makeStyles({
    stepDetail: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        paddingLeft: tokens.spacingHorizontalXXL,
        paddingBottom: tokens.spacingVerticalS,
    },
    decisionRow: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXXS,
        padding: tokens.spacingHorizontalS,
        borderLeft: `${tokens.strokeWidthThickest} solid ${tokens.colorNeutralStroke2}`,
        marginBottom: tokens.spacingVerticalXS,
    },
    decisionApproveText: {
        color: tokens.colorStatusSuccessForeground1,
    },
    decisionRejectText: {
        color: tokens.colorStatusDangerForeground1,
    },
});
