import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditEventTableStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        flex: 1,
        width: "100%",
        minWidth: 0,
        minHeight: 0,
        overflow: "hidden",
    },
    tableScroller: {
        flex: 1,
        width: "100%",
        minHeight: 0,
        overflow: "auto",
        overscrollBehavior: "contain",
    },
    table: {
        minWidth: "640px",
    },
    tableHeader: {
        position: "sticky",
        top: 0,
        zIndex: 1,
        backgroundColor: tokens.colorNeutralBackground1,
    },
    row: {
        cursor: "pointer",
    },
    footer: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
        flexShrink: 0,
        paddingTop: tokens.spacingVerticalS,
        "@media (max-width: 600px)": {
            alignItems: "flex-start",
            flexDirection: "column",
        },
    },
    paginationControls: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },
    paginationPageIndicator: {
        display: "flex",
        alignItems: "center",
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
        padding: tokens.spacingVerticalL,
        textAlign: "center",
    },
});
