import {makeStyles, tokens} from "@fluentui/react-components";

export const useAssigneeBuilderStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
    },

    row: {
        display: "flex",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalS,
        padding: tokens.spacingHorizontalS,
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
        gap: tokens.spacingHorizontalS,
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
        gap: tokens.spacingHorizontalS,
    },

    emptyHint: {
        color: tokens.colorNeutralForeground3,
        fontStyle: "italic",
    },
});

