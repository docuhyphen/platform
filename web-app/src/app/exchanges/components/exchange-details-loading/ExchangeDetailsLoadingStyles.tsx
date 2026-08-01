import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useExchangeDetailsLoadingStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        flex: 1,
        minWidth: 0,
        minHeight: 0,
        width: "100%",
        boxSizing: "border-box",
        "@media (max-width: 768px)": {
            gap: tokens.spacingHorizontalS,
        },
    },

    heading: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        background: tokens.colorNeutralBackground1,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalL}`,
        borderRadius: tokens.borderRadiusMedium,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRight: `1px solid ${tokens.colorNeutralStroke2}`,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorNeutralStroke2,
        boxShadow: tokens.shadow4,
        width: "100%",
        boxSizing: "border-box",
        minWidth: 0,
        "@media (max-width: 768px)": {
            padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalS}`,
            gap: tokens.spacingHorizontalXS,
        },
    },

    headerLine: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalS,
        justifyContent: "space-between",
        alignItems: "center",
        width: "100%",
        minWidth: 0,
    },

    name: {
        flex: 1,
        minWidth: "80px",
        maxWidth: "360px",
    },

    exchangeActions: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalS,
        flexShrink: 0,
        "@media (max-width: 768px)": {
            gap: tokens.spacingHorizontalXXS,
        },
        "@media (max-width: 480px)": {
            "& > :nth-child(-n+3)": {
                display: "none",
            },
        },
    },

    compactAction: {
        flexShrink: 0,
    },

    tabsHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalL,
        minWidth: 0,
        width: "100%",
        "@media (max-width: 768px)": {
            alignItems: "stretch",
            flexDirection: "column",
            gap: tokens.spacingVerticalXS,
        },
    },

    tabsViewport: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flex: 1,
        minWidth: 0,
        overflow: "hidden",
    },

    tabItem: {
        width: "112px",
        maxWidth: "28%",
        flexShrink: 0,
    },

    tabActions: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexShrink: 0,
    },

    progressSummary: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexShrink: 0,
    },

    progressLabel: {
        width: "104px",
        "@media (max-width: 480px)": {
            display: "none",
        },
    },

    progressBar: {
        width: "88px",
    },

    documentCardListContainer: {
        overflow: "hidden",
        boxSizing: "border-box",
        width: "100%",
    },

    stripLayout: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr)",
        alignItems: "center",
        minWidth: 0,
        width: "100%",
    },

    documentCardList: {
        display: "flex",
        overflowX: "hidden",
        overflowY: "hidden",
        ...shorthands.padding(tokens.spacingVerticalXS, tokens.spacingHorizontalXXS),
        gap: tokens.spacingHorizontalM,
        minWidth: 0,
    },

    documentCard: {
        width: "300px",
        minWidth: "300px",
        maxWidth: "300px",
        flex: "0 0 300px",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        boxSizing: "border-box",
        ...shorthands.padding(tokens.spacingVerticalM, tokens.spacingHorizontalM),
        ...shorthands.borderLeft("5px", "solid", "transparent"),
        "@media (max-width: 768px)": {
            width: "min(300px, calc(100vw - 48px))",
            minWidth: "min(300px, calc(100vw - 48px))",
            maxWidth: "min(300px, calc(100vw - 48px))",
            flexBasis: "min(300px, calc(100vw - 48px))",
        },
    },

    documentTitle: {
        width: "168px",
        maxWidth: "100%",
    },

    documentFooter: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    documentStatus: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
        flex: 1,
    },

    documentStatusText: {
        width: "84px",
        minWidth: 0,
    },

    documentActions: {
        display: "flex",
        alignItems: "center",
        flexShrink: 0,
        gap: tokens.spacingHorizontalXXS,
    },

    documentUploadAction: {
        width: "72px",
        flexShrink: 0,
    },

    pdfPreviewSection: {
        position: "relative",
        display: "flex",
        justifyContent: "center",
        flex: 1,
        background: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        overflow: "hidden",
        width: "100%",
        minWidth: 0,
        minHeight: 0,
        boxSizing: "border-box",
        padding: tokens.spacingHorizontalL,
        "@media (max-width: 768px)": {
            padding: tokens.spacingHorizontalS,
        },
    },

    previewPage: {
        width: "min(100%, 520px)",
        minHeight: "360px",
        alignSelf: "flex-start",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        backgroundColor: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusSmall,
        boxSizing: "border-box",
        padding: tokens.spacingHorizontalL,
    },

    previewLineWide: {
        width: "64%",
    },

    previewLineMedium: {
        width: "42%",
    },

    previewBlock: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        width: "100%",
    },

    previewLine: {
        width: "100%",
    },
});
