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
        alignItems: "center",
        gap: "0.75rem",
        padding: "0.75rem 1rem",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
    },

    rowName: {
        flex: 1,
        minWidth: 0,
    },

    rowTrigger: {
        width: "14rem",
        flexShrink: 0,
        color: tokens.colorNeutralForeground3,
    },

    rowActions: {
        display: "flex",
        gap: "4px",
        flexShrink: 0,
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

