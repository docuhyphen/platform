import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XL,
    WIDTH_CONTENT,
    WIDTH_SUBTITLE,
} from "../shared.ts";

const stepReveal = {
    from: {opacity: 0, transform: "translateY(2rem) scale(0.94)"},
    to: {opacity: 1, transform: "translateY(0) scale(1)"},
};

const connectorReveal = {
    from: {transform: "scaleX(0)"},
    to: {transform: "scaleX(1)"},
};

const endDotPulse = {
    "0%, 100%": {transform: "scale(1)"},
    "50%": {transform: "scale(1.18)"},
};

const illustrationBase = {
    width: "100%",
    aspectRatio: "1.2",
    backgroundRepeat: "no-repeat",
    backgroundPosition: "center",
    backgroundSize: "cover",
    backgroundColor: tokens.colorNeutralBackground1,
    borderRadius: "0.5rem",
    overflow: "hidden",
};

export const useHowItWorksSectionStyles = makeStyles({
    section: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        backgroundColor: tokens.colorNeutralBackground1,

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

    eyebrow: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeBase300,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.04em",
        textTransform: "uppercase",
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

    steps: {
        display: "grid",
        gridTemplateColumns: "repeat(4, minmax(0, 1fr))",
        gap: SPACE_MD,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_LG,
        },
    },

    step: {
        display: "flex",
        flexDirection: "column",
        alignItems: "stretch",
        opacity: 0,
        transform: "translateY(2rem) scale(0.94)",

        "@media (prefers-reduced-motion: reduce)": {
            opacity: 1,
            transform: "none",
        },
    },

    stepVisible: {
        animationName: stepReveal,
        animationDuration: "2.7s",
        animationTimingFunction: "cubic-bezier(0.16, 1, 0.3, 1)",
        animationFillMode: "both",
    },

    stepFirst: {animationDelay: "100ms"},
    stepSecond: {animationDelay: "220ms"},
    stepThird: {animationDelay: "340ms"},
    stepFourth: {animationDelay: "460ms"},

    stepTimeline: {
        position: "relative",
        display: "flex",
        alignItems: "center",
        marginBottom: SPACE_SM,

        "::after": {
            content: "''",
            position: "absolute",
            top: "50%",
            left: "3.25rem",
            width: "calc(100% - 3.25rem)",
            height: tokens.strokeWidthThin,
            backgroundColor: tokens.colorBrandStroke1,
            transformOrigin: "left",
            transform: "scaleX(0)",
        },

        [BREAKPOINT_MOBILE]: {
            "::after": {
                display: "none",
            },
        },

        "@media (prefers-reduced-motion: reduce)": {
            "::after": {
                transform: "scaleX(1)",
            },
        },
    },

    stepMarker: {
        position: "relative",
        zIndex: 1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "3.25rem",
        height: "3.25rem",
        color: tokens.colorNeutralForegroundOnBrand,
        backgroundColor: tokens.colorBrandBackground,
        border: `1px solid ${tokens.colorBrandStroke2}`,
        borderRadius: "50%",
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,

    },

    lastStepTimeline: {
        "::before": {
            content: "''",
            position: "absolute",
            zIndex: 1,
            top: "calc(50% - 0.25rem)",
            right: 0,
            width: "0.5rem",
            height: "0.5rem",
            backgroundColor: tokens.colorBrandBackground,
            borderRadius: "50%",
            animationName: endDotPulse,
            animationDuration: "3.8s",
            animationTimingFunction: "ease-in-out",
            animationIterationCount: "infinite",
        },
    },

    connectorVisible: {
        "::after": {
            animationName: connectorReveal,
            animationDuration: "2.8s",
            animationTimingFunction: "cubic-bezier(0.16, 1, 0.3, 1)",
            animationFillMode: "both",
        },
    },

    lastStepMarker: {
        zIndex: 2,
    },

    number: {
        fontWeight: tokens.fontWeightSemibold,
    },

    stepCard: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: SPACE_SM,
        height: "100%",
        padding: SPACE_MD,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: "1rem",
    },

    stepLabel: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.04em",
        textTransform: "uppercase",
    },

    stepTitle: {
        color: tokens.colorNeutralForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    stepDescription: {
        color: tokens.colorNeutralForeground2,
    },

    illustrationOne: {
        ...illustrationBase,
        backgroundImage:
            "url('/illustrations/how-it-works-step-1.png')",
    },

    illustrationTwo: {
        ...illustrationBase,
        backgroundImage:
            "url('/illustrations/how-it-works-step-2.png')",
    },

    illustrationThree: {
        ...illustrationBase,
        backgroundImage:
            "url('/illustrations/how-it-works-step-3.png')",
    },

    illustrationFour: {
        ...illustrationBase,
        backgroundImage:
            "url('/illustrations/how-it-works-step-4.png')",
    },
});
