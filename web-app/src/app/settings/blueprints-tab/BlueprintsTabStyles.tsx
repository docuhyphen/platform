import {makeStyles, tokens} from "@fluentui/react-components";

export const useBlueprintEditorStyles = makeStyles({
    dialogSurface: {
        maxWidth: "600px",
        width: "100%",
    },
    tabList: {
        marginBottom: tokens.spacingVerticalL,
    },
    accordionPanelContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        paddingBottom: tokens.spacingVerticalS,
    },
    linkedBadgeRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        marginTop: tokens.spacingVerticalSNudge,
    },
    permissionsContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
    },
    errorSpan: {
        color: tokens.colorPaletteRedForeground1,
        fontSize: "12px",
        marginTop: tokens.spacingVerticalS,
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
        gap: tokens.spacingHorizontalS,
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
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        color: tokens.colorNeutralForeground3,
    },
    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: tokens.spacingHorizontalM,
        "@media screen and (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },
    tabContainer: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalL,
        width: "100%",
        minWidth: 0,
        flexWrap: "wrap",
    },
    header: {
        width: "100%",
        display: "flex",
        marginBottom: tokens.spacingVerticalL,
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
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
        gap: tokens.spacingHorizontalS,
        paddingBottom: tokens.spacingVerticalS,
        flexShrink: 0,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
    scrollableContent: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    },
    headerRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
    },
    filterPopover: {
        padding: tokens.spacingHorizontalS,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
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
        gap: tokens.spacingHorizontalXS,
    },
    blueprintCard: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalS,
    },
    blueprintCardContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        flex: "1",
        minWidth: 0,
    },
    summaryText: {
        color: tokens.colorNeutralForeground2,
    },
    badgeRow: {
        display: "flex",
        gap: tokens.spacingHorizontalSNudge,
        flexWrap: "wrap",
        alignItems: "center",
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
    },
    table: {
        width: "100%",
        borderCollapse: "collapse",
    },
    th: {
        textAlign: "left",
        padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalM}`,
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
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        verticalAlign: "middle",
    },
});
