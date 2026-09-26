import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewHeaderStyles = makeStyles({
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalL,
        flexWrap: "wrap",
    },
    titleGroup: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        minWidth: 0,
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
});
