import {makeStyles} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, SPACE_MD} from "../../shared.ts";

const stepReveal = {
    from: {opacity: 0, transform: "translateY(2rem) scale(0.94)"},
    to: {opacity: 1, transform: "translateY(0) scale(1)"},
};

export const useHowItWorksDesktopStepsStyles = makeStyles({
    desktopSteps: {
        display: "grid",
        gridTemplateColumns: "repeat(4, minmax(0, 1fr))",
        gap: SPACE_MD,

        [BREAKPOINT_MOBILE]: {
            display: "none",
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
});
