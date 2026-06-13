import {makeStyles, tokens} from "@fluentui/react-components";

export const useAssigneeBuilderStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "0.5rem",
    },

    row: {
        display: "flex",
        alignItems: "flex-start",
        gap: "0.5rem",
        padding: "0.5rem",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
    },

    rowFields: {
        flex: 1,
        display: "flex",
        flexWrap: "wrap",
        gap: "0.5rem",
    },

    kindSelect: {
        minWidth: "8rem",
    },

    fieldInput: {
        flex: 1,
        minWidth: "8rem",
    },

    addRow: {
        display: "flex",
        alignItems: "center",
        gap: "0.5rem",
    },

    emptyHint: {
        color: tokens.colorNeutralForeground3,
        fontStyle: "italic",
    },
});

