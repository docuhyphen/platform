import {makeStyles, tokens} from "@fluentui/react-components";

export const useStructuredResponseConflictReviewStyles = makeStyles({
    review: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingHorizontalM,
        borderRadius: tokens.borderRadiusMedium,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorStatusWarningBorder1}`,
        backgroundColor: tokens.colorStatusWarningBackground1,
    },
    title: {
        fontWeight: tokens.fontWeightSemibold,
    },
    conflicts: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        margin: 0,
        padding: 0,
        listStyleType: "none",
    },
    conflict: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        columnGap: tokens.spacingHorizontalM,
        rowGap: tokens.spacingVerticalXS,
    },
    answer: {
        display: "flex",
        flexDirection: "column",
        flexGrow: 1,
        minWidth: 0,
        overflowWrap: "anywhere",
    },
});
