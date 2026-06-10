import {makeStyles, tokens} from "@fluentui/react-components";

export const useSettingsStyles = makeStyles({
    container: {
        alignItems: "flex-start",
        display: "flex",
        flexDirection: "row",
        boxSizing: "border-box",
        padding: "2rem",
        width: "100%",
        maxWidth: "70rem",
        flex: "1",
        alignSelf: "center",
        gap: "1rem",
        margin: "auto",
        marginTop: "80px",
        background: tokens.colorNeutralBackground1,
        height: "calc(100% - 4rem)",
        overflow: "hidden",
        // Tighten the chrome on tablet/phone so the settings card no longer
        // overflows the viewport on screens narrower than ~980px (previous
        // fixed width) and so the inner content has room to breathe.
        "@media (max-width: 1024px)": {
            padding: "16px 24px",
            marginTop: "72px",
            height: "calc(100% - 88px)",
        },
        "@media (max-width: 768px)": {
            padding: "12px 12px",
            marginTop: "68px",
            height: "calc(100% - 76px)",
            rowGap: "12px",
            boxShadow: "none",
        },
        "@media (max-width: 480px)": {
            padding: "8px 8px",
        },
    },
    tabsContainer: {
        maxHeight: "100%",
        width: "100%",
        // overflow: "auto",
        padding: "0 1rem",
        height: "100%"
    }
    // Allow the FluentUI TabList to scroll horizontally on narrow screens
    // instead of clipping or wrapping awkwardly. The TabList itself stays
    // inline-flex; the wrapping rules keep tab labels from breaking mid-word.
    // tabList: {
    //     maxWidth: "100%",
    //     overflowX: "auto",
    //     overflowY: "hidden",
    //     flexShrink: 0,
    //     "& > *": {
    //         flexShrink: 0,
    //     },
    // },
    // tabs: {
    //     width: "100%",
    //     minWidth: 0,
    //     overflowX: "hidden",
    //     height: "100%",
    //     padding: "0 1rem"
    // },
});