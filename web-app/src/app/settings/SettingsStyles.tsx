import {makeStyles, tokens} from "@fluentui/react-components";

/** Height of the fixed app header exch- keep in sync with GlobalStyles.mainAppHeader (60px = 3.75rem) */
export const SETTINGS_HEADER_HEIGHT = "3.75rem";

export const useSettingsStyles = makeStyles({

    container: {
        height: "100%",
        overflowY: "auto",
        overflowX: "hidden",
        background: tokens.colorNeutralBackground1,
        boxSizing: "border-box",
        paddingTop: SETTINGS_HEADER_HEIGHT,
    },

    layout: {
        display: "flex",
        flexDirection: "row",
        alignItems: "flex-start",
        maxWidth: "70rem",
        margin: "0 auto",
        padding: "1rem 2rem 3rem 2rem",
        boxSizing: "border-box",
        gap: "2rem",
        minHeight: "100%",
        "@media (max-width: 1024px)": {
            padding: "1rem 1.5rem 3rem 1.5rem",
        },
        "@media (max-width: 768px)": {
            flexDirection: "column",
            padding: "0.5rem 0.75rem 3rem 0.75rem",
        },
    },

    sidebarWrapper: {
        position: "sticky",
        top: SETTINGS_HEADER_HEIGHT,
        alignSelf: "flex-start",
        flexShrink: 0,
        width: "13.125rem",
        maxHeight: `calc(100vh - ${SETTINGS_HEADER_HEIGHT})`,
        overflowY: "auto",
        "@media (max-width: 768px)": {
            display: "none",
        },
    },

    mobileMenuBar: {
        display: "none",
        "@media (max-width: 768px)": {
            display: "flex",
            alignItems: "center",
            gap: "0.5rem",
            position: "sticky",
            top: 0,
            zIndex: 10,
            background: tokens.colorNeutralBackground1,
            padding: "0.375rem 0.75rem",
            borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        },
    },

    tabsContainer: {
        flex: 1,
        paddingTop: "2rem",
        minWidth: 0,
        "@media (max-width: 768px)": {
            width: "100%",
            paddingTop: "1rem",
        },
    },
    tabSettingDivider: {
        marginTop: tokens.spacingVerticalM
    }
});