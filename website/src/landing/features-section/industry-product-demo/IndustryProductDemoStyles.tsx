import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE} from "../../shared.ts";

export const useIndustryProductDemoStyles = makeStyles({
    shell: {
        position: "relative",
        width: "100%",
        minWidth: 0,
        height: "39rem",
        overflow: "hidden",
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow16,

        [BREAKPOINT_MOBILE]: {
            height: "auto",
        },

        ":before": {
            position: "absolute",
            top: 0,
            right: 0,
            bottom: 0,
            width: "6rem",
            zIndex: 1,
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
            zIndex: 2,
            pointerEvents: "none",
            backgroundImage: `linear-gradient(to bottom, transparent 0%, ${tokens.colorNeutralBackground1} 100%)`,
            content: '""',
        },
    },

    scaledViewport: {
        width: "116.28%",
        transform: "scale(0.86)",
        transformOrigin: "top left",

        [BREAKPOINT_MOBILE]: {
            width: "100%",
            transform: "none",
        },
    },

    body: {
        display: "flex",
        minHeight: "42rem",

        [BREAKPOINT_MOBILE]: {
            minHeight: "32rem",
        },
    },
});
