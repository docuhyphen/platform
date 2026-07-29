import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    BUTTON_MIN_WIDTH,
    MENU_PADDING_DESKTOP,
    MENU_PADDING_MOBILE,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    WIDTH_CONTENT,
} from "../shared.ts";

export const useLandingHeaderStyles = makeStyles({
    wrapper: {
        width: "100%",
        padding: MENU_PADDING_DESKTOP,
        boxSizing: "border-box",
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackgroundAlpha,
        backdropFilter: "blur(1rem)",
        zIndex: 1000,

        [BREAKPOINT_MOBILE]: {
            padding: MENU_PADDING_MOBILE,
        },
    },

    fixedWrapper: {
        position: "fixed",
        top: 0,
        right: 0,
        left: 0,
        boxShadow: tokens.shadow4,
    },

    nav: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        width: WIDTH_CONTENT,
        maxWidth: "100%",
        margin: "0 auto",
        justifyContent: "space-between",
        paddingTop: SPACE_MD,
        paddingBottom: SPACE_MD,
        gap: SPACE_MD,
    },

    leftGroup: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_LG,
        flex: 1,
        minWidth: 0,
    },

    logo: {
        display: "inline-flex",
        flexShrink: 0,
    },

    desktopLinks: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_LG,

        "@media (max-width: 56em)": {
            display: "none",
        },
    },

    rightActions: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_SM,
        flexShrink: 0,
    },

    navLink: {
        position: "relative",
        display: "inline-flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXXS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalXS}`,
        border: 0,
        color: tokens.colorNeutralForeground1,
        backgroundColor: "transparent",
        textDecorationLine: "none",
        fontSize: tokens.fontSizeBase300,
        cursor: "pointer",

        ":hover": {
            color: tokens.colorBrandForeground1,
        },
    },

    activeNavLink: {
        color: tokens.colorBrandForeground1,

        "::after": {
            content: '""',
            position: "absolute",
            left: tokens.spacingHorizontalXS,
            right: tokens.spacingHorizontalXS,
            bottom: 0,
            height: "2px",
            borderRadius: tokens.borderRadiusCircular,
            backgroundColor: tokens.colorCompoundBrandStroke,
        },
    },

    signInButton: {
        minWidth: BUTTON_MIN_WIDTH,

        [BREAKPOINT_MOBILE]: {
            minWidth: "auto",
            paddingLeft: SPACE_SM,
            paddingRight: SPACE_SM,
        },
    },

    tryFreeButton: {
        minWidth: BUTTON_MIN_WIDTH,

        [BREAKPOINT_MOBILE]: {
            display: "none",
        },
    },

    mobileMenuButton: {
        display: "none",

        "@media (max-width: 56em)": {
            display: "inline-flex",
        },
    },

    mainMobileMenu: {
        display: "flex",
        gap: tokens.spacingVerticalXXS,
    },

    mainMobileMenuItem: {
        backgroundColor: tokens.colorNeutralBackground2,
    },

    activeMenuItem: {
        position: "relative",
        color: tokens.colorBrandForeground1,

        "::after": {
            content: '""',
            position: "absolute",
            left: tokens.spacingHorizontalXS,
            right: tokens.spacingHorizontalXS,
            bottom: 0,
            height: "2px",
            borderRadius: tokens.borderRadiusCircular,
            backgroundColor: tokens.colorCompoundBrandStroke,
        },
    },

    activeSubMenuItem: {
        color: tokens.colorBrandForeground1,
    },

    mainMobileSubMenuItem: {
        paddingLeft: tokens.spacingHorizontalXL,
    },
});
