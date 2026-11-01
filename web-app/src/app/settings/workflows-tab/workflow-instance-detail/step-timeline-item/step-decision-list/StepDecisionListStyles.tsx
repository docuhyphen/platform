import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepDecisionListStyles = makeStyles({
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
});
