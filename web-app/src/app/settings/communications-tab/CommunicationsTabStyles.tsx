import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationsTabStyles = makeStyles({
    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: tokens.spacingHorizontalM,
        "@media screen and (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },
    outerContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        width: "100%",
        height: "100%",
        minHeight: 0,
    },
    headerRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        background: tokens.colorNeutralBackground1,
        paddingBottom: tokens.spacingVerticalS,
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
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
    commCard: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalS,
    },
    commCardContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        flex: "1",
        minWidth: 0,
    },
    summaryText: {
        color: tokens.colorNeutralForeground2,
    },
    subjectText: {
        color: tokens.colorNeutralForeground3,
        fontStyle: "italic",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
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

export const useCommunicationEditorStyles = makeStyles({
    tagInput: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
        alignItems: "center",
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        minHeight: "32px",
        cursor: "text",
    },
    dialogSurface: {
        maxWidth: "680px",
        width: "100%",
    },
    tabList: {
        marginBottom: tokens.spacingVerticalL,
    },
    tabContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
    tokenHintLabel: {
        color: tokens.colorNeutralForeground3,
        display: "block",
        marginBottom: tokens.spacingVerticalSNudge,
    },
    tokenHintRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
    },
    tokenHintMore: {
        color: tokens.colorNeutralForeground3,
    },
    tokenBadge: {
        fontFamily: "monospace",
        cursor: "pointer",
    },
    previewResultsContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
    previewBox: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: tokens.spacingHorizontalM,
        background: tokens.colorNeutralBackground2,
    },
    previewSubjectLabel: {
        display: "block",
        marginBottom: tokens.spacingVerticalXS,
        color: tokens.colorNeutralForeground3,
    },
    previewBodyLabel: {
        display: "block",
        marginBottom: tokens.spacingVerticalS,
        color: tokens.colorNeutralForeground3,
    },
    previewPre: {
        fontFamily: "inherit",
        whiteSpace: "pre-wrap",
        wordBreak: "break-word",
        margin: 0,
    },
    errorSpan: {
        color: tokens.colorPaletteRedForeground1,
        fontSize: "12px",
        marginTop: tokens.spacingVerticalS,
        display: "block",
    },
    renderPreviewButton: {
        alignSelf: "flex-start",
    },
    previewErrorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    tagInputField: {
        border: "none",
        flexGrow: 1,
        minWidth: "8rem",
    },
});
