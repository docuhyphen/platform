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
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "8px",
        width: "100%",
        "@media screen and (max-width: 600px)": {
            alignItems: "stretch",
            flexDirection: "column",
        },
    },
    searchRowInputs: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingVerticalS,
        flex: "0 1 auto",
        minWidth: 0,
        "@media screen and (max-width: 600px)": {
            width: "100%",
        },
    },
    searchField: {
        flex: "0 1 420px",
        minWidth: 0,
        width: "420px",
        maxWidth: "100%",
        "@media screen and (max-width: 600px)": {
            flex: 1,
            width: "auto",
        },
    },
    filterEmptyText: {
        padding: "4px 8px",
        color: tokens.colorNeutralForeground3,
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
        gap: 0,
        width: "100%",
        height: "100%",
        minHeight: 0,
    },
    stickyBlock: {
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        paddingBottom: "8px",
        flexShrink: 0,
        paddingInline: "0.5rem",
        boxSizing: "border-box",
    },
    scrollableContent: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: "0.5rem",
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: "16px",
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
    table: {
        width: "100%",
        borderCollapse: "collapse",
    },
    th: {
        textAlign: "left",
        padding: "6px 12px",
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground3,
        borderBottom: `1px solid ${tokens.colorNeutralStroke1}`,
        whiteSpace: "nowrap",
    },
    tr: {
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        ":hover": {backgroundColor: tokens.colorNeutralBackground2},
    },
    td: {
        padding: "8px 12px",
        verticalAlign: "middle",
    },
});
