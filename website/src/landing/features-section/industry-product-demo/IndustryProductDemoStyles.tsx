import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE} from "../../shared.ts";

export const INDUSTRY_DEMO_CANVAS_WIDTH_REM = 90;
export const INDUSTRY_DEMO_CANVAS_HEIGHT_REM = 45.75;
export const INDUSTRY_DEMO_DEFAULT_SCALE = 1;

export const useIndustryProductDemoStyles = makeStyles({
    shell: {
        position: "relative",
        isolation: "isolate",
        width: "100%",
        maxWidth: "100%",
        minWidth: 0,
        height: `calc(${INDUSTRY_DEMO_CANVAS_HEIGHT_REM}rem * var(--industry-demo-scale, ${INDUSTRY_DEMO_DEFAULT_SCALE}))`,
        overflow: "hidden",
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow16,

        [BREAKPOINT_MOBILE]: {
            width: "100vw",
            maxWidth: "100vw",
            marginLeft: "calc(50% - 50vw)",
            marginRight: "calc(50% - 50vw)",
            borderRadius: 0,
        },

        ":before": {
            position: "absolute",
            top: 0,
            right: 0,
            bottom: 0,
            width: "6rem",
            zIndex: 2,
            pointerEvents: "none",
            backgroundImage: `linear-gradient(to right, transparent 0%, ${tokens.colorNeutralBackground1} 100%)`,
            content: '""',
        },

        ":after": {
            position: "absolute",
            right: 0,
            bottom: 0,
            left: 0,
            height: "6rem",
            zIndex: 3,
            pointerEvents: "none",
            backgroundImage: `linear-gradient(to bottom, transparent 0%, ${tokens.colorNeutralBackground1} 100%)`,
            content: '""',
        },
    },

    scaledViewport: {
        position: "relative",
        zIndex: 0,
        width: `${INDUSTRY_DEMO_CANVAS_WIDTH_REM}rem`,
        height: `${INDUSTRY_DEMO_CANVAS_HEIGHT_REM}rem`,
        transform: `scale(var(--industry-demo-scale, ${INDUSTRY_DEMO_DEFAULT_SCALE}))`,
        transformOrigin: "top left",
    },

    body: {
        display: "flex",
        minHeight: "42rem",
    },
});
