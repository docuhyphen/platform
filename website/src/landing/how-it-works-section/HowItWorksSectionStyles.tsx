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

const progressFill = {
    from: {width: "0%"},
    to: {width: "100%"},
};

const completionDotReveal = {
    "0%, 99%": {opacity: 0, transform: "scale(0.7)"},
    "100%": {opacity: 1, transform: "scale(1)"},
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
    transitionProperty: "filter, opacity",
    transitionDuration: tokens.durationNormal,
    transitionTimingFunction: tokens.curveEasyEase,
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

    pauseButton: {
        marginLeft: SPACE_SM,
        flexShrink: 0,
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

    desktopSteps: {
        display: "grid",
        gridTemplateColumns: "repeat(4, minmax(0, 1fr))",
        gap: SPACE_MD,

        [BREAKPOINT_MOBILE]: {
            display: "none",
        },
    },

    mobileCarouselWrapper: {
        display: "none",

        [BREAKPOINT_MOBILE]: {
            display: "flex",
            flexDirection: "column",
            gap: SPACE_MD,
        },
    },

    mobileCarousel: {
        width: "100%",
    },

    mobileCarouselSlider: {
        width: "100%",
    },

    mobileCarouselCard: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
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

        [BREAKPOINT_MOBILE]: {
            width: "100%",
        },

        "@media (prefers-reduced-motion: reduce)": {
            width: "100%",
        },
    },

    mobileStepHeader: {
        display: "flex",
        alignItems: "center",
        width: "100%",
        marginBottom: SPACE_SM,
    },

    stepProgressTrack: {
        position: "relative",
        flexGrow: 1,
        height: "0.5rem",
        marginLeft: SPACE_SM,
        backgroundColor: tokens.colorNeutralBackground3,
        borderRadius: "999px",
        overflow: "hidden",

        [BREAKPOINT_MOBILE]: {
            display: "none",
        },
    },

    lastStepProgressTrack: {
        minWidth: "3.5rem",
    },

    mobileStepProgressTrack: {
        position: "relative",
        flexGrow: 1,
        height: "0.5rem",
        marginLeft: SPACE_SM,
        backgroundColor: tokens.colorNeutralBackground3,
        borderRadius: "999px",
        overflow: "hidden",
    },

    stepProgressFill: {
        width: "0%",
        height: "100%",
        backgroundColor: tokens.colorBrandBackground,
        borderRadius: "inherit",
    },

    stepProgressComplete: {
        width: "100%",

        "@media (prefers-reduced-motion: reduce)": {
            width: "100%",
        },
    },

    stepProgressActive: {
        animationName: progressFill,
        animationDuration: "8s",
        animationTimingFunction: "linear",
        animationFillMode: "forwards",

        "@media (prefers-reduced-motion: reduce)": {
            width: "100%",
            animationName: "none",
        },
    },

    pausedAnimation: {
        animationPlayState: "paused",
    },

    stepMarker: {
        position: "relative",
        zIndex: 1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "3.25rem",
        height: "3.25rem",
        color: tokens.colorBrandForeground1,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorBrandStroke2}`,
        borderRadius: "50%",
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        transitionProperty: "background-color, color, border-color, box-shadow",
        transitionDuration: tokens.durationNormal,
        transitionTimingFunction: tokens.curveEasyEase,
    },

    activeStepMarker: {
        color: tokens.colorNeutralForegroundOnBrand,
        backgroundColor: tokens.colorBrandBackground,
        borderColor: tokens.colorBrandBackground,
        boxShadow: tokens.shadow4,
    },

    connectorVisible: {
        "@media (prefers-reduced-motion: no-preference)": {
            animationName: connectorReveal,
            animationDuration: "600ms",
            animationTimingFunction: "cubic-bezier(0.16, 1, 0.3, 1)",
            animationFillMode: "both",
        },
    },

    lastStepMarker: {
        zIndex: 2,
    },

    stepCompletionDot: {
        width: "0.5rem",
        height: "0.5rem",
        marginLeft: SPACE_SM,
        opacity: 0,
        backgroundColor: tokens.colorBrandBackground,
        borderRadius: "50%",
        transform: "scale(0.7)",

        [BREAKPOINT_MOBILE]: {
            display: "none",
        },
    },

    mobileStepCompletionDot: {
        width: "0.5rem",
        height: "0.5rem",
        marginLeft: SPACE_SM,
        opacity: 0,
        backgroundColor: tokens.colorBrandBackground,
        borderRadius: "50%",
        transform: "scale(0.7)",
    },

    stepCompletionDotPending: {
        animationName: `${completionDotReveal}, ${endDotPulse}`,
        animationDuration: "8s, 3.8s",
        animationTimingFunction: "linear, ease-in-out",
        animationDelay: "0s, 8s",
        animationFillMode: "forwards, none",
        animationIterationCount: "1, infinite",

        "@media (prefers-reduced-motion: reduce)": {
            opacity: 1,
            transform: "scale(1)",
            animationName: "none",
        },
    },

    stepCompletionDotVisible: {
        opacity: 1,
        transform: "scale(1)",
        animationName: endDotPulse,
        animationDuration: "3.8s",
        animationTimingFunction: "ease-in-out",
        animationIterationCount: "infinite",

        "@media (prefers-reduced-motion: reduce)": {
            animationName: "none",
        },
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
        transitionProperty: "background-color, color, border-color, box-shadow, transform",
        transitionDuration: tokens.durationNormal,
        transitionTimingFunction: tokens.curveEasyEase,
    },

    activeStepCard: {
        backgroundColor: tokens.colorBrandBackground,
        borderColor: "transparent",
        boxShadow: tokens.shadow8,
        transform: "translateY(-0.25rem)",
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

    activeStepText: {
        color: tokens.colorNeutralForegroundOnBrand,
    },

    activeIllustration: {
        filter: "brightness(0.96) saturate(1.05)",
    },

    mobileNavigation: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: SPACE_SM,
    },

    mobileNavButton: {
        flex: 1,
        minWidth: 0,
    },

    mobileStepCount: {
        color: tokens.colorNeutralForeground2,
        fontWeight: tokens.fontWeightSemibold,
        whiteSpace: "nowrap",
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
