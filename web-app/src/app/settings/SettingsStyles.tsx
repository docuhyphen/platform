import {makeStyles, tokens} from "@fluentui/react-components";

export const useSettingsStyles = makeStyles({
    container: {
        alignItems: "flex-start",
        display: "flex",
        flexDirection: "column",
        boxSizing: "border-box",
        padding: "16px 60px",
        width: "100%",
        maxWidth: "980px",
        flex: "1",
        rowGap: "20px",
        margin: "auto",
        marginTop: "80px",
        minHeight: "480px",
        boxShadow: tokens.shadow4,
        background: tokens.colorNeutralBackground1,
        height: "calc(100% - 140px)",
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
    // Allow the FluentUI TabList to scroll horizontally on narrow screens
    // instead of clipping or wrapping awkwardly. The TabList itself stays
    // inline-flex; the wrapping rules keep tab labels from breaking mid-word.
    tabList: {
        maxWidth: "100%",
        overflowX: "auto",
        overflowY: "hidden",
        flexShrink: 0,
        "& > *": {
            flexShrink: 0,
        },
    },
    tabs: {
        width: "100%",
        minWidth: 0,
        overflowX: "hidden",
    },
});