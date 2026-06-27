import {makeStyles, tokens} from "@fluentui/react-components";

export const useBlueprintEditorStyles = makeStyles({
    dialogSurface: {
        maxWidth: "600px",
        width: "100%",
    },
    tabList: {
        marginBottom: "16px",
    },
    accordionPanelContent: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
        paddingBottom: "8px",
    },
    linkedBadgeRow: {
        display: "flex",
        alignItems: "center",
        gap: "8px",
        marginTop: "6px",
    },
    permissionsContent: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },
    errorSpan: {
        color: "var(--colorPaletteRedForeground1)",
        fontSize: "12px",
        marginTop: "8px",
        display: "block",
    },
    tagInputField: {
        border: "none",
        flexGrow: 1,
        minWidth: "8rem",
    },
});

export const useTemplatesTabStyles = makeStyles({
    searchRow: {
        display: "flex",
        alignItems: "center",
        gap: "8px",
        width: "100%",
    },
    paginationRow: {
        display: "flex",
        justifyContent: "center",
        paddingTop: "4px",
    },
    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: "12px",
        "@media screen and (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },
    tabContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px",
        width: "100%",
        minWidth: 0,
        flexWrap: "wrap",
    },
    header: {
        width: "100%",
        display: "flex",
        marginBottom: "16px",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: "8px",
    },
    outerContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        width: "100%",
    },
    stickyBlock: {
        position: "sticky",
        top: 0,
        zIndex: 1,
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        paddingBottom: "8px",
    },
    headerRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
    },
    filterPopover: {
        padding: "8px",
        display: "flex",
        flexDirection: "column",
        gap: "6px",
        minWidth: "180px",
        maxWidth: "260px",
    },
    filterPopoverList: {
        minHeight: "80px",
        maxHeight: "220px",
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
    },
    activeTagsRow: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: "4px",
    },
    blueprintCard: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: "12px 16px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: "8px",
    },
    blueprintCardContent: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        flex: "1",
        minWidth: 0,
    },
    summaryText: {
        color: "var(--colorNeutralForeground2)",
    },
    badgeRow: {
        display: "flex",
        gap: "6px",
        flexWrap: "wrap",
        alignItems: "center",
    },
    errorText: {
        color: "var(--colorPaletteRedForeground1)",
    },
    emptyText: {
        color: "var(--colorNeutralForeground3)",
    },
});