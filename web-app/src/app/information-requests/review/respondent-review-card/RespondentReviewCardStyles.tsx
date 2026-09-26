import {makeStyles, tokens} from "@fluentui/react-components";

export const useRespondentReviewCardStyles = makeStyles({
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingVerticalM,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground1,
        minWidth: 0,
        overflowWrap: "anywhere",
    },
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
    },
    finding: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
    correction: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
});
