import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: 0,
        height: "100%",
        minHeight: 0,
    },

    tabListWrapper: {
        background: tokens.colorNeutralBackground1,
        paddingBottom: tokens.spacingVerticalXS,
        boxSizing: "border-box",
        minHeight: "2.75rem",
        flexShrink: 0,
        paddingInline: tokens.spacingHorizontalS,
    },

    content: {
        flex: 1,
        minHeight: 0,
        overflow: "hidden",
    },
    transitionFrame: {
        height: "100%",
        minHeight: 0,
        minWidth: 0,
        animationFillMode: "both",
        willChange: "opacity, transform",
        "@media (prefers-reduced-motion: reduce)": {
            animationName: "none",
            animationDuration: "0ms",
            transform: "none",
        },
    },
    slideInFromRight: {
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
    slideInFromLeft: {
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
    scrollableContent: {
        height: "100%",
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
});
