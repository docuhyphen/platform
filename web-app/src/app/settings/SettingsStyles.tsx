import {makeStyles, tokens} from "@fluentui/react-components";

/** Height of the fixed app header — keep in sync with GlobalStyles.mainAppHeader (60px = 3.75rem) */
export const SETTINGS_HEADER_HEIGHT = "3.75rem";

export const useSettingsStyles = makeStyles({
    /**
     * Outermost settings wrapper.
     * Acts as the scroll container for the whole settings page so that the
     * app header, the left sidebar, and any inner section headers can each
     * declare `position:sticky` relative to this element.
     *
     * padding-top offsets the fixed app header so content is never hidden
     * behind it.  No margin-top needed because the container starts at y=0.
     */
    container: {
        height: "100%",
        overflowY: "auto",
        overflowX: "hidden",
        background: tokens.colorNeutralBackground1,
        boxSizing: "border-box",
        paddingTop: SETTINGS_HEADER_HEIGHT,
    },

    /**
     * Inner flex-row that holds the sidebar and content pane.
     * Centred with a max-width cap and some breathing room.
     */
    layout: {
        display: "flex",
        flexDirection: "row",
        alignItems: "flex-start",
        maxWidth: "70rem",
        margin: "0 auto",
        padding: "1rem 2rem 3rem 2rem",
        boxSizing: "border-box",
        gap: "1rem",
        minHeight: "100%",
        "@media (max-width: 1024px)": {
            padding: "1rem 1.5rem 3rem 1.5rem",
        },
        "@media (max-width: 768px)": {
            flexDirection: "column",
            padding: "0.5rem 0.75rem 3rem 0.75rem",
        },
    },

    /**
     * Sticky left navigation sidebar (desktop only).
     * Sticks just below the fixed app header.
     */
    sidebarWrapper: {
        position: "sticky",
        top: SETTINGS_HEADER_HEIGHT,
        alignSelf: "flex-start",
        flexShrink: 0,
        width: "13.125rem",
        "@media (max-width: 768px)": {
            display: "none",
        },
    },

    /**
     * Sticky hamburger bar shown on mobile instead of the sidebar.
     * Hidden on desktop.
     */
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

    /** Main content pane that holds the rendered tab component. */
    tabsContainer: {
        flex: 1,
        paddingTop: "2rem",
        minWidth: 0,
        "@media (max-width: 768px)": {
            width: "100%",
            paddingTop: "1rem",
        },
    },
});