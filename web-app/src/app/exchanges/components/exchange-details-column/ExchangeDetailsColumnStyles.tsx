import {makeStyles} from "@fluentui/react-components";

export const useExchangeDetailsColumnStyles = makeStyles({
    detailsTransitionFrame: {
        flex: 1,
        minWidth: 0,
        minHeight: 0,
        display: "flex",
        animationFillMode: "both",
        willChange: "opacity, transform",
        "@media (prefers-reduced-motion: reduce)": {
            animationName: "none",
            animationDuration: "0ms",
            transform: "none",
        },
    },
    detailsSlideInFromRight: {
        animationName: {
            from: {
                opacity: 0,
                transform: "translateX(28px)",
            },
            to: {
                opacity: 1,
                transform: "translateX(0)",
            },
        },
        animationDuration: "190ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },
});
