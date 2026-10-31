import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewAssignmentRowStyles = makeStyles({
    assignment: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    actions: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
    },
    detail: {
        color: tokens.colorNeutralForeground2,
    },
});
