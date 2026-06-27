import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowInstanceDashboardStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "1rem",
    },

    filterBar: {
        display: "flex",
        gap: "0.75rem",
        justifyContent: "end",
        alignItems: "center",
        flexWrap: "wrap",
    },

    row: {
        display: "flex",
        alignItems: "center",
        gap: "0.75rem",
        padding: "0.75rem 0",
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        cursor: "pointer",
        ":hover": {
            background: tokens.colorNeutralBackground2,
        },
    },

    rowInfo: {
        flex: 1,
        minWidth: 0,
    },

    rowMeta: {
        color: tokens.colorNeutralForeground3,
        flexShrink: 0,
    },

    emptyState: {
        padding: "2rem",
        color: tokens.colorNeutralForeground3,
        textAlign: "center",
    },

    pagination: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: "0.5rem",
        paddingTop: "0.75rem",
    },

    statusFilterSelect: {
        minWidth: "10rem",
    },

    rowMetaInherit: {
        color: "inherit",
    },
});

