import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowInstanceDetailStyles = makeStyles({
    stepTimeline: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        paddingTop: tokens.spacingVerticalL,
    },

    exchangeName: {
        marginBottom: tokens.spacingVerticalS,
    },

    instanceStatus: {
        marginBottom: tokens.spacingVerticalM,
        color: tokens.colorNeutralForeground3,
    },

    failureMessage: {
        marginBottom: tokens.spacingVerticalM,
        color: tokens.colorStatusDangerForeground1,
    },
});
