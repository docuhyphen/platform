import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SPACE_MD,
    SPACE_SM,
    SPACE_XS,
    WIDTH_CONTENT,
} from "../shared.ts";

export const useIndustryCarouselSectionStyles = makeStyles({
    section: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        width: "100%",
        display: "grid",
        gap: SPACE_MD,
    },

    viewport: {
        overflow: "hidden",
        borderRadius: "3.125rem 3.125rem 0 0",
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground2,

        [BREAKPOINT_MOBILE]: {
            borderRadius: "1.25rem 1.25rem 0 0",
        },
    },

    track: {
        display: "flex",
        transitionProperty: "transform",
        transitionDuration: tokens.durationSlow,
        transitionTimingFunction: "ease",
        willChange: "transform",
    },

    slide: {
        minWidth: "100%",
    },

    imagePlaceholder: {
        aspectRatio: "1300 / 700",
        width: "100%",
        boxSizing: "border-box",
        position: "relative",
        overflow: "hidden",
        padding: `${tokens.spacingVerticalXXXL} ${tokens.spacingHorizontalXXXL} 0`,

        [BREAKPOINT_MOBILE]: {
            padding: `${tokens.spacingVerticalXL} ${tokens.spacingHorizontalXL} 0`,
        },
    },

    imageFrame: {
        position: "relative",
        width: "100%",
        height: "100%",
        overflow: "hidden",
        borderRadius: "0.875rem 0.875rem 0 0",
    },

    image: {
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
        objectFit: "cover",
    },

    imageOverlay: {
        position: "absolute",
        left: SPACE_MD,
        bottom: SPACE_MD,
        zIndex: 1,
    },

    titleCard: {
        maxWidth: "32rem",
        padding: `${SPACE_SM} ${SPACE_MD}`,
        borderRadius: CARD_RADIUS,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow8,
        color: tokens.colorNeutralStroke2,
    },

    titleText: {
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,

        [BREAKPOINT_MOBILE]: {
            fontSize: tokens.fontSizeBase300,
            lineHeight: tokens.lineHeightBase300,
        },
    },

    controls: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: SPACE_MD,
    },

    indicators: {
        display: "flex",
        flexWrap: "wrap",
        justifyContent: "center",
        alignItems: "center",
        gap: SPACE_XS,
    },

    indicator: {
        width: "0.625rem",
        height: "0.625rem",
        minWidth: "unset",
        minHeight: "unset",
        padding: 0,
        border: "none",
        backgroundColor: tokens.colorNeutralStroke2,
        transitionProperty: "background-color, transform",
        transitionDuration: tokens.durationNormal,
        transitionTimingFunction: "ease",

        ":hover": {
            backgroundColor: tokens.colorNeutralStroke1,
        },

        ":hover:active": {
            backgroundColor: tokens.colorNeutralStroke1,
        },
    },

    indicatorActive: {
        backgroundColor: tokens.colorBrandForeground1,
        transform: "scale(1.2)",

        ":hover": {
            backgroundColor: tokens.colorBrandForeground1,
        },

        ":hover:active": {
            backgroundColor: tokens.colorBrandForeground1,
        },
    },

    industryVisualLaw: {
        background: "linear-gradient(135deg, #20344d 0%, #385980 55%, #7ea6d6 100%)",
    },

    industryVisualRealEstate: {
        background: "linear-gradient(135deg, #2d3953 0%, #4b678c 52%, #99b1cf 100%)",
    },

    industryVisualAccounting: {
        background: "linear-gradient(135deg, #2e3558 0%, #5c6da9 50%, #a2b5e8 100%)",
    },

    industryVisualHealthcare: {
        background: "linear-gradient(135deg, #24505a 0%, #3f8391 52%, #8fd0dc 100%)",
    },

    industryVisualBanking: {
        background: "linear-gradient(135deg, #243f63 0%, #3f6ba1 54%, #84b5ea 100%)",
    },
});
