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
        minWidth: 0,
        "@media (max-width: 900px)": {
            flexWrap: "wrap",
        },
    },

    rowFields: {
        flex: 1,
        display: "flex",
        flexWrap: "wrap",
        gap: "0.5rem",
        minWidth: 0,
    },

    kindSelect: {
        flex: "1 1 10rem",
        minWidth: 0,
    },

    fieldInput: {
        flex: "1 1 10rem",
        minWidth: 0,
    },

    removeButton: {
        flexShrink: 0,
        "@media (max-width: 900px)": {
            marginLeft: "auto",
        },
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

