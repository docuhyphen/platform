import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, SPACE_SM} from "../../shared.ts";

const connectorReveal = {
    from: {transform: "scaleX(0)"},
    to: {transform: "scaleX(1)"},
};

const endDotPulse = {
    "0%, 100%": {transform: "scale(1)"},
    "50%": {transform: "scale(1.18)"},
};

export const useHowItWorksStepTimelineStyles = makeStyles({
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

    pauseButton: {
        marginLeft: SPACE_SM,
        flexShrink: 0,
    },

    stepProgressBar: {
        position: "relative",
        flexGrow: 1,
        flexShrink: 1,
        flexBasis: 0,
        minWidth: 0,
        marginLeft: SPACE_SM,

        [BREAKPOINT_MOBILE]: {
            display: "none",
        },
    },

    lastStepProgressTrack: {
        minWidth: "3.5rem",
    },

    mobileStepProgressBar: {
        position: "relative",
        flexGrow: 1,
        flexShrink: 1,
        flexBasis: 0,
        minWidth: 0,
        marginLeft: SPACE_SM,
    },

    stepMarker: {
        position: "relative",
        zIndex: 1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0,
        flexGrow: 0,
        flexBasis: "3.25rem",
        width: "3.25rem",
        minWidth: "3.25rem",
        height: "3.25rem",
        minHeight: "3.25rem",
        aspectRatio: "1 / 1",
        color: tokens.colorBrandForeground1,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorBrandStroke2}`,
        borderRadius: tokens.borderRadiusCircular,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        transitionProperty: "background-color, color, border-color, box-shadow",
        transitionDuration: tokens.durationNormal,
        transitionTimingFunction: tokens.curveEasyEase,
    },

    activeStepMarker: {
        color: tokens.colorNeutralForegroundOnBrand,
        backgroundColor: tokens.colorBrandBackground,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorBrandBackground}`,
        boxShadow: tokens.shadow4,
    },

    lastStepMarker: {
        zIndex: 2,
    },

    connectorVisible: {
        "@media (prefers-reduced-motion: no-preference)": {
            animationName: connectorReveal,
            animationDuration: "600ms",
            animationTimingFunction: "cubic-bezier(0.16, 1, 0.3, 1)",
            animationFillMode: "both",
        },
    },

    stepCompletionDot: {
        width: "0.5rem",
        height: "0.5rem",
        marginLeft: SPACE_SM,
        opacity: 0,
        backgroundColor: tokens.colorBrandBackground,
        borderRadius: tokens.borderRadiusCircular,
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
        borderRadius: tokens.borderRadiusCircular,
        transform: "scale(0.7)",
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
        lineHeight: tokens.lineHeightBase200,
    },
});
