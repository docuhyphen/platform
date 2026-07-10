import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationsTabStyles = makeStyles({
    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: "12px",
        "@media screen and (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },
    outerContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        width: "100%",
        height: "100%",
        minHeight: 0,
    },
    headerRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        background: tokens.colorNeutralBackground1,
        paddingBottom: "8px",
        flexWrap: "wrap",
        gap: "8px",
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
    commCard: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: "12px 16px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: "8px",
    },
    commCardContent: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        flex: "1",
        minWidth: 0,
    },
    summaryText: {
        color: "var(--colorNeutralForeground2)",
    },
    subjectText: {
        color: "var(--colorNeutralForeground3)",
        fontStyle: "italic",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
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

export const useCommunicationEditorStyles = makeStyles({
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
    dialogSurface: {
        maxWidth: "680px",
        width: "100%",
    },
    tabList: {
        marginBottom: "16px",
    },
    tabContent: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
    },
    tokenHintLabel: {
        color: "var(--colorNeutralForeground3)",
        display: "block",
        marginBottom: "6px",
    },
    tokenHintRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: "4px",
    },
    tokenHintMore: {
        color: "var(--colorNeutralForeground3)",
    },
    tokenBadge: {
        fontFamily: "monospace",
        cursor: "pointer",
    },
    previewResultsContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
    },
    previewBox: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: "12px",
        background: "var(--colorNeutralBackground2)",
    },
    previewSubjectLabel: {
        display: "block",
        marginBottom: "4px",
        color: "var(--colorNeutralForeground3)",
    },
    previewBodyLabel: {
        display: "block",
        marginBottom: "8px",
        color: "var(--colorNeutralForeground3)",
    },
    previewPre: {
        fontFamily: "inherit",
        whiteSpace: "pre-wrap",
        wordBreak: "break-word",
        margin: 0,
    },
    errorSpan: {
        color: "var(--colorPaletteRedForeground1)",
        fontSize: "12px",
        marginTop: "8px",
        display: "block",
    },
    renderPreviewButton: {
        alignSelf: "flex-start",
    },
    previewErrorText: {
        color: "var(--colorPaletteRedForeground1)",
    },
    tagInputField: {
        border: "none",
        flexGrow: 1,
        minWidth: "8rem",
    },
});
