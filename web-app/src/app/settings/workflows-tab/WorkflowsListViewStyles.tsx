import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowsListViewStyles = makeStyles({
    section: {
        marginBottom: "2rem",
    },

    sectionHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        marginBottom: "0.75rem",
    },

    emptyState: {
        padding: "1.5rem",
        color: tokens.colorNeutralForeground3,
        textAlign: "center",
        border: `1px dashed ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
    },

    row: {
        display: "flex",
        flexDirection: "column",
        gap: "6px",
        padding: "0.75rem 1rem",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        minWidth: 0,
        overflow: "hidden",
    },

    topRow: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "8px",
        minWidth: 0,
    },

    triggerText: {
        flexShrink: 0,
        color: tokens.colorNeutralForeground3,
    },

    middleRow: {
        display: "flex",
        alignItems: "center",
        gap: "8px",
        minWidth: 0,
    },

    description: {
        flex: 1,
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
        minWidth: 0,
        color: tokens.colorNeutralForeground2,
    },

    middleActions: {
        display: "flex",
        alignItems: "center",
        gap: "4px",
        flexShrink: 0,
    },

    tagsContainer: {
        display: "flex",
        overflow: "hidden",
        gap: "4px",
        minWidth: 0,
    },

    templateCard: {
        padding: "1rem",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        display: "flex",
        alignItems: "flex-start",
        gap: "1rem",
    },

    templateCardInfo: {
        flex: 1,
        minWidth: 0,
    },

    tagRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: "4px",
        marginTop: "4px",
    },

    errorBar: {
        marginBottom: "0.75rem",
    },

    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: "12px",
        "@media screen and (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },
});
