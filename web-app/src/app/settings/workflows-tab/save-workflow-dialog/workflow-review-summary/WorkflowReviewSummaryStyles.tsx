import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowReviewSummaryStyles = makeStyles({
    mutedText: {
        color: tokens.colorNeutralForeground3,
    },
    reviewSection: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
    reviewRow: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXXS,
    },
    reviewLabel: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        textTransform: "uppercase",
        letterSpacing: "0.04em",
    },
});
