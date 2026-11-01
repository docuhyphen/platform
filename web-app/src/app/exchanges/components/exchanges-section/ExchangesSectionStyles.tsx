import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangesSectionStyles = makeStyles({
    container: {
        display: "flex",
        gap: tokens.spacingHorizontalL,
        height: "100%",
        width: "100%",
        padding: `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalM}) ${tokens.spacingHorizontalL} ${tokens.spacingVerticalL} ${tokens.spacingHorizontalL}`,
        boxSizing: "border-box",
        "@media (max-width: 768px)": {
            flexDirection: "column",
            gap: tokens.spacingHorizontalS,
            padding: `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXS}) ${tokens.spacingHorizontalS} ${tokens.spacingVerticalS} ${tokens.spacingHorizontalS}`,
        },
    },
    listPaneWrapper: {
        display: "contents",
        "@media (max-width: 768px)": {
            display: "flex",
            flex: 1,
            minHeight: 0,
            width: "100%",
        },
    },
    listPaneHidden: {
        "@media (max-width: 768px)": {
            display: "none",
        },
    },
    listPaneSlideInFromLeft: {
        "@media (max-width: 768px)": {
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
            animationFillMode: "both",
            willChange: "opacity, transform",
        },
        "@media (prefers-reduced-motion: reduce)": {
            animationName: "none",
            animationDuration: "0ms",
            transform: "none",
        },
    },
});
