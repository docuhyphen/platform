import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowFormColumnStyles = makeStyles({
    splitFormColumn: {
        flex: "0 0 40%",
        height: "100%",
        minWidth: 0,
        minHeight: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingRight: tokens.spacingHorizontalXL,
        paddingBottom: tokens.spacingVerticalM,
        boxSizing: "border-box",
        "@media (max-width: 1100px)": {
            flex: "1 1 auto",
            width: "100%",
            height: "auto",
            paddingRight: tokens.spacingHorizontalM,
        },
    },
    formPageTransitionFrame: {
        minWidth: 0,
        minHeight: "100%",
        animationFillMode: "both",
        willChange: "opacity, transform",
        "@media (prefers-reduced-motion: reduce)": {
            animationName: "none",
            animationDuration: "0ms",
            transform: "none",
        },
    },
    formPageSlideLeft: {
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
    formPageSlideRight: {
        animationName: {
            from: {
                opacity: 0,
                transform: "translateX(-28px)",
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
