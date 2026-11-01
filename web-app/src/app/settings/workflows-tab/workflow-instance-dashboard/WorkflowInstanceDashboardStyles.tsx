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

    emptyState: {
        padding: tokens.spacingHorizontalXXXL,
        color: tokens.colorNeutralForeground3,
        textAlign: "center",
    },
});
