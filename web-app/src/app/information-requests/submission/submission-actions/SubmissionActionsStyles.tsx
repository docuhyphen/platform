import {makeStyles, tokens} from "@fluentui/react-components";

export const useSubmissionActionsStyles = makeStyles({
    actions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
    pending: {
        color: tokens.colorStatusWarningForeground1,
        minWidth: 0,
    },
});
