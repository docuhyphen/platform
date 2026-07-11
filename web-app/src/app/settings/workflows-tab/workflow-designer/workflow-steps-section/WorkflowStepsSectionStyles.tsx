import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowStepsSectionStyles = makeStyles({
    transitionFrame: {
        minWidth: 0,
        animationFillMode: "both",
        willChange: "opacity, transform",
        "@media (prefers-reduced-motion: reduce)": {
            animationName: "none",
            animationDuration: "0ms",
            transform: "none",
        },
    },
    slideLeft: {
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
    slideRight: {
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
    stepDetail: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },

    stepList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },

    stepListHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        position: "sticky",
        top: 0,
        zIndex: 2,
        paddingBlock: tokens.spacingVerticalS,
        backgroundColor: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        paddingInline: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },

    headerTitle: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    noStepsText: {
        color: tokens.colorNeutralForeground3,
    },
});
