import {makeStyles, tokens} from "@fluentui/react-components";

/** Height of the fixed app header exch- keep in sync with GlobalStyles.mainAppHeader (60px = 3.75rem) */
export const SETTINGS_HEADER_HEIGHT = "3.75rem";

export const useSettingsStyles = makeStyles({

    container: {
        height: "100%",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
        background: tokens.colorNeutralBackground1,
        boxSizing: "border-box",
        paddingTop: SETTINGS_HEADER_HEIGHT,
    },

    layout: {
        display: "flex",
        flexDirection: "row",
        alignItems: "stretch",
        flex: 1,
        minHeight: 0,
        width: "100%",
        maxWidth: "85rem",
        margin: "0 auto",
        padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalXXXL} calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalL}) ${tokens.spacingHorizontalXXXL}`,
        boxSizing: "border-box",
        gap: tokens.spacingHorizontalXXXL,
        "@media (max-width: 1024px)": {
            padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalXXL} calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalL}) ${tokens.spacingHorizontalXXL}`,
        },
        "@media (max-width: 768px)": {
            flexDirection: "column",
            padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM} calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalL}) ${tokens.spacingHorizontalM}`,
            gap: 0,
        },
    },

    sidebarWrapper: {
        flexShrink: 0,
        width: "13.125rem",
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
        scrollbarWidth: "none",
        "&::-webkit-scrollbar": {
            width: 0,
        },
        "&:hover": {
            scrollbarWidth: "thin",
            scrollbarColor: `${tokens.colorNeutralStroke1} transparent`,
        },
        "&:hover::-webkit-scrollbar": {
            width: "0.5rem",
        },
        "&:hover::-webkit-scrollbar-thumb": {
            backgroundColor: tokens.colorNeutralStroke1,
        },
        "&:hover::-webkit-scrollbar-track": {
            backgroundColor: "transparent",
        },
        "@media (max-width: 768px)": {
            display: "none",
        },
    },

    mobileMenuBar: {
        display: "none",
        "@media (max-width: 768px)": {
            display: "flex",
            alignItems: "center",
            gap: tokens.spacingHorizontalS,
            position: "sticky",
            top: 0,
            zIndex: 10,
            background: tokens.colorNeutralBackground1,
            padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalM}`,
            borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        },
    },

    tabsContainer: {
        flex: 1,
        minWidth: 0,
        minHeight: 0,
        overflow: "hidden",
        "@media (max-width: 768px)": {
            width: "100%",
        },
    },
    managedTabPanel: {
        height: "100%",
        minHeight: 0,
        overflow: "hidden",
    },
    tabPanelScroller: {
        height: "100%",
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
    tabSettingDivider: {
        marginTop: tokens.spacingVerticalM
    }
});
