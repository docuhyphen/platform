import {makeStyles, tokens} from "@fluentui/react-components";

export const useDocumentsTabStyles = makeStyles({
    container: {
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
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: "8px",
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
    card: {
        minWidth: "0",
        border: "1px solid var(--colorNeutralStroke1)",
        borderRadius: tokens.borderRadiusXLarge,
        padding: "12px 16px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: "8px",
    },
    cardBody: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        flex: "1",
        minWidth: "0",
    },
    badgeRow: {
        display: "flex",
        gap: "6px",
        flexWrap: "wrap",
        alignItems: "center",
    },
    cardActions: {
        display: "flex",
        gap: "4px",
        alignItems: "center",
        flexShrink: "0",
    },
    dialogBody: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        paddingTop: "8px",
    },
    dialogActions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: "8px",
        marginTop: "8px",
    },
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
    tagInput: {
        display: "flex",
        flexWrap: "wrap",
        gap: "4px",
        alignItems: "center",
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: "4px 8px",
        minHeight: "32px",
        cursor: "text",
    },
    tagInputField: {
        border: "none",
        flexGrow: "1",
        minWidth: "8rem",
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
        padding: "6px 12px",
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground3,
        borderBottom: `1px solid ${tokens.colorNeutralStroke1}`,
        whiteSpace: "nowrap",
    },
    tr: {
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        ":hover": {
            backgroundColor: tokens.colorNeutralBackground2,
        },
    },
    td: {
        padding: "8px 12px",
        verticalAlign: "middle",
    },
    descriptionText: {
        color: tokens.colorNeutralForeground2,
    },
    selectedFileText: {
        color: tokens.colorNeutralForeground2,
    },
});
