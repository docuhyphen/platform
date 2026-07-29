import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    BUTTON_MIN_WIDTH,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
} from "../shared.ts";

export const useHeroSectionStyles = makeStyles({
    wrapper: {
        width: "100%",
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        position: "relative",
        overflow: "hidden",
        backgroundImage: `radial-gradient(circle at 82% 42%, ${tokens.colorBrandBackground2} 0%, transparent 35%), linear-gradient(135deg, ${tokens.colorNeutralBackground2} 0%, ${tokens.colorNeutralBackground1} 100%)`,

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    container: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr) minmax(30rem, 0.9fr)",
        alignItems: "center",
        gap: tokens.spacingHorizontalXXXL,
        maxWidth: "73rem",
        minHeight: "38rem",
        margin: "0 auto",
        paddingTop: tokens.spacingVerticalXXXL,
        paddingBottom: tokens.spacingVerticalXXXL,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "minmax(0, 1fr)",
            gap: `calc(${tokens.spacingVerticalXXXL} * 2)`,
            minHeight: "auto",
            paddingTop: tokens.spacingVerticalXXL,
            paddingBottom: tokens.spacingVerticalXXL,
        },
    },

    content: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: tokens.spacingVerticalL,
        position: "relative",
        zIndex: 1,

        [BREAKPOINT_MOBILE]: {
            alignItems: "center",
            textAlign: "center",
        },
    },

    title: {
        maxWidth: "42rem",
        paddingTop: tokens.spacingVerticalXS,
        paddingBottom: tokens.spacingVerticalXS,
        backgroundImage: `linear-gradient(90deg, ${tokens.colorBrandForeground1} 0%, ${tokens.colorBrandForeground2} 100%)`,
        backgroundClip: "text",
        WebkitBackgroundClip: "text",
        WebkitTextFillColor: "transparent",
        color: "transparent",
        fontSize: "3.75rem",
        lineHeight: "4.5rem",
        fontWeight: tokens.fontWeightSemibold,

        [BREAKPOINT_MOBILE]: {
            fontSize: tokens.fontSizeHero800,
            lineHeight: tokens.lineHeightHero800,
        },
    },

    supportingText: {
        maxWidth: "38rem",
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,
    },

    productDefinition: {
        maxWidth: "38rem",
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase300,
        lineHeight: tokens.lineHeightBase400,
    },

    actions: {
        display: "flex",
        alignItems: "flex-start",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalM,
        marginTop: tokens.spacingVerticalL,

        [BREAKPOINT_MOBILE]: {
            flexDirection: "column",
            justifyContent: "flex-start",
            gap: tokens.spacingHorizontalS,
            width: "100%",
        },
    },

    buttonBase: {
        minWidth: BUTTON_MIN_WIDTH,
    },

    primaryCta: {
        minWidth: "12rem",
        minHeight: "2.5rem",
        fontSize: tokens.fontSizeBase400,
        lineHeight: tokens.lineHeightBase400,
        fontWeight: tokens.fontWeightSemibold,
        boxShadow: tokens.shadow8,
    },

});
