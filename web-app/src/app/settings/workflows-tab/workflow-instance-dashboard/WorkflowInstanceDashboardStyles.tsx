import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowInstanceDashboardStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        height: "100%",
        minHeight: "100%",
    },
    listContent: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
    },

    filterBar: {
        display: "flex",
        gap: tokens.spacingHorizontalM,
        justifyContent: "end",
        alignItems: "center",
        flexWrap: "wrap",
    },

    row: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        padding: `${tokens.spacingVerticalM} 0`,
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
        padding: tokens.spacingHorizontalXXXL,
        color: tokens.colorNeutralForeground3,
        textAlign: "center",
    },

    statusFilterSelect: {
        minWidth: "10rem",
    },

    rowMetaInherit: {
        color: "inherit",
    },
});

