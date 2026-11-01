import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowReviewStepListStyles = makeStyles({
    mutedText: {
        color: tokens.colorNeutralForeground3,
    },
    mutedSpan: {
        color: tokens.colorNeutralForeground3,
    },
    stepList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },
    stepItem: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },
    stepNumber: {
        width: "20px",
        height: "20px",
        borderRadius: tokens.borderRadiusCircular,
        backgroundColor: tokens.colorNeutralBackground3,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        flexShrink: 0,
    },
});
