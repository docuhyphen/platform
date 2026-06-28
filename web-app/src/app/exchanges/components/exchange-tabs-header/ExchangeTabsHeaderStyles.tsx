import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeTabsHeaderStyles = makeStyles({
    container: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalL,
        minWidth: 0,
        "@media (max-width: 768px)": {
            alignItems: "stretch",
            flexDirection: "column",
            gap: tokens.spacingVerticalXS,
        },
    },
    tabsViewport: {
        flex: 1,
        minWidth: 0,
        overflowX: "auto",
        overflowY: "hidden",
        scrollbarWidth: "none",
        "&::-webkit-scrollbar": {
            display: "none",
        },
    },
    headerActions: {
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
    progressText: {
        color: tokens.colorNeutralForeground3,
        whiteSpace: "nowrap",
    },
    progressBar: {
        width: "88px",
    },
});
