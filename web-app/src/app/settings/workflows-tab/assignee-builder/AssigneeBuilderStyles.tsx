import {makeStyles, tokens} from "@fluentui/react-components";

export const useAssigneeBuilderStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
    },

    addRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },

    emptyHint: {
        color: tokens.colorNeutralForeground3,
        fontStyle: "italic",
    },
});
