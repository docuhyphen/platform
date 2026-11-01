import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_SM,
    SPACE_XL,
    WIDTH_CONTENT,
    WIDTH_SUBTITLE,
} from "../shared.ts";

export const useHowItWorksSectionStyles = makeStyles({
    section: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        backgroundColor: tokens.colorNeutralBackground5,

        "@media (prefers-reduced-motion: reduce)": {
            "& *": {
                animationName: "none !important",
                transitionDuration: "0.01ms !important",
            },
        },

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    container: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_XL,
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: SPACE_SM,
    },

    title: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeHero800,
        fontWeight: tokens.fontWeightSemibold,
    },

    subtitle: {
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForeground2,
    },
});
