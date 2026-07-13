import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE} from "../../shared.ts";

const activityPulse = {
    "0%, 12%, 100%": {boxShadow: tokens.shadow4, opacity: 0.6, transform: "scale(1)"},
    "5%": {boxShadow: tokens.shadow16, opacity: 1, transform: "scale(1.04)"},
};

const eventReveal = {
    "0%, 55%, 100%": {opacity: 0, transform: "translateY(0.5rem) scale(0.96)"},
    "60%, 78%": {opacity: 1, transform: "translateY(0) scale(1)"},
};

const wireFlow = {
    from: {strokeDashoffset: 0},
    to: {strokeDashoffset: "-1.5rem"},
};

export const useExchangeOrchestrationCanvasStyles = makeStyles({
    canvas: {
        position: "relative",
        width: "100%",
        minWidth: 0,
        height: "34rem",
        isolation: "isolate",

        "@media (prefers-reduced-motion: reduce)": {
            "& *": {
                animationDuration: "0.01ms !important",
                animationIterationCount: "1 !important",
            },
        },

        [BREAKPOINT_MOBILE]: {
            maxWidth: "34rem",
            height: "31rem",
            margin: "0 auto",
        },
    },

    wireLayer: {
        position: "absolute",
        zIndex: 1,
        inset: 0,
        width: "100%",
        height: "100%",
        overflow: "visible",
        pointerEvents: "none",
    },

    wire: {
        fill: "none",
        stroke: tokens.colorBrandStroke1,
        strokeWidth: tokens.strokeWidthThin,
        strokeLinecap: "round",
        strokeDasharray: "0.5rem 0.35rem",
        opacity: 0.65,
        animationName: wireFlow,
        animationDuration: "3s",
        animationTimingFunction: "linear",
        animationIterationCount: "infinite",
    },

    node: {
        position: "absolute",
        zIndex: 2,
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusCircular,
        backgroundColor: tokens.colorNeutralBackground1,
        color: tokens.colorNeutralForeground2,
        boxShadow: tokens.shadow4,
        whiteSpace: "nowrap",
        animationName: activityPulse,
        animationDuration: "12s",
        animationTimingFunction: "ease-in-out",
        animationIterationCount: "infinite",

        "& svg": {
            color: tokens.colorBrandForeground1,
        },

        [BREAKPOINT_MOBILE]: {
            zIndex: 4,
            gap: tokens.spacingHorizontalXXS,
            padding: `${tokens.spacingVerticalXXS} ${tokens.spacingHorizontalXS}`,
            fontSize: tokens.fontSizeBase100,

            "& svg": {
                width: "0.875rem",
                height: "0.875rem",
            },
        },
    },

    nodeParticipants: {
        top: "4%",
        left: "2%",
        animationDelay: "0s",

        [BREAKPOINT_MOBILE]: {top: "15%"},
    },
    nodeDocuments: {
        top: "3%",
        right: "2%",
        animationDelay: "2s",

        [BREAKPOINT_MOBILE]: {top: "15%"},
    },
    nodeWorkflows: {
        top: "43%",
        right: "-2%",
        animationDelay: "4s",

        [BREAKPOINT_MOBILE]: {top: "39%"},
    },
    nodeNotifications: {
        bottom: "5%",
        right: "3%",
        animationDelay: "6s",

        [BREAKPOINT_MOBILE]: {bottom: "15%"},
    },
    nodeAudit: {
        bottom: "4%",
        left: "4%",
        animationDelay: "8s",

        [BREAKPOINT_MOBILE]: {bottom: "15%"},
    },
    nodeIntegrations: {
        top: "43%",
        left: "-2%",
        animationDelay: "10s",

        [BREAKPOINT_MOBILE]: {top: "39%"},
    },

    exchangeCard: {
        position: "absolute",
        zIndex: 3,
        top: "50%",
        left: "50%",
        width: "19rem",
        padding: tokens.spacingVerticalL,
        transform: "translate(-50%, -50%)",
        border: `${tokens.strokeWidthThin} solid ${tokens.colorBrandStroke2}`,
        borderRadius: tokens.borderRadiusXLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow28,
        boxSizing: "border-box",

        "::before": {
            content: "\"\"",
            position: "absolute",
            zIndex: -1,
            inset: "-1.25rem",
            borderRadius: tokens.borderRadiusXLarge,
            backgroundColor: tokens.colorBrandBackground2,
            opacity: 0.45,
            filter: "blur(1.25rem)",
        },

        [BREAKPOINT_MOBILE]: {
            width: "14rem",
            padding: tokens.spacingVerticalS,
        },
    },

    cardHeader: {
        display: "grid",
        gridTemplateColumns: "auto 1fr auto",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },

    exchangeIcon: {
        display: "grid",
        placeItems: "center",
        width: "2.5rem",
        height: "2.5rem",
        borderRadius: tokens.borderRadiusMedium,
        color: tokens.colorBrandForeground1,
        backgroundColor: tokens.colorBrandBackground2,
    },

    cardEyebrow: {
        display: "block",
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase100,
        textTransform: "uppercase",
        letterSpacing: "0.06em",
    },

    cardTitle: {
        display: "block",
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase400,
    },

    activeBadge: {
        padding: `${tokens.spacingVerticalXXS} ${tokens.spacingHorizontalS}`,
        borderRadius: tokens.borderRadiusCircular,
        color: tokens.colorStatusSuccessForeground1,
        backgroundColor: tokens.colorStatusSuccessBackground1,
        fontSize: tokens.fontSizeBase100,
        fontWeight: tokens.fontWeightSemibold,
    },

    summary: {
        display: "flex",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalXS,
        paddingTop: tokens.spacingVerticalM,
        paddingBottom: tokens.spacingVerticalM,
        color: tokens.colorNeutralForeground3,
    },

    activityList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },

    activityRow: {
        display: "grid",
        gridTemplateColumns: "auto 1fr auto",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minHeight: "2.5rem",
        paddingLeft: tokens.spacingHorizontalS,
        paddingRight: tokens.spacingHorizontalS,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
        color: tokens.colorNeutralForeground2,

        "& svg": {
            width: "1rem",
            height: "1rem",
        },

        [BREAKPOINT_MOBILE]: {
            minHeight: "2.25rem",
            paddingLeft: tokens.spacingHorizontalXS,
            paddingRight: tokens.spacingHorizontalXS,
        },
    },

    activeActivityRow: {
        backgroundColor: tokens.colorBrandBackground2,
        color: tokens.colorBrandForeground1,
    },

    activityLabel: {fontSize: tokens.fontSizeBase200},
    activityValue: {fontSize: tokens.fontSizeBase100, color: tokens.colorNeutralForeground3},
    completeIcon: {color: tokens.colorStatusSuccessForeground1},

    liveStatus: {
        padding: `${tokens.spacingVerticalXXS} ${tokens.spacingHorizontalXS}`,
        borderRadius: tokens.borderRadiusCircular,
        backgroundColor: tokens.colorNeutralBackground1,
        fontSize: tokens.fontSizeBase100,
        fontWeight: tokens.fontWeightSemibold,
    },

    eventChip: {
        position: "absolute",
        zIndex: 4,
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        borderRadius: tokens.borderRadiusCircular,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow8,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        opacity: 0,
        animationName: eventReveal,
        animationDuration: "12s",
        animationTimingFunction: "ease-in-out",
        animationIterationCount: "infinite",

        "& svg": {
            width: "1rem",
            height: "1rem",
        },
    },

    eventApproved: {
        top: "26%",
        right: "3%",
        color: tokens.colorStatusSuccessForeground1,
        animationDelay: "0s",
    },

    eventAudit: {
        bottom: "25%",
        left: "2%",
        color: tokens.colorBrandForeground1,
        animationDelay: "5s",
    },
});
