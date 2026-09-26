import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewItemContentStyles = makeStyles({
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        padding: tokens.spacingVerticalS,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
        minWidth: 0,
        overflowWrap: "anywhere",
    },
    value: {
        fontWeight: tokens.fontWeightSemibold,
    },
    evidence: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
});
