import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, SPACE_SM, SPACE_XL, WIDTH_CONTENT} from "../shared.ts";

export const useIndustryExperienceStyles = makeStyles({
    section: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_XL,
        width: "100%",
        maxWidth: `min(92rem, calc(100vw - ${tokens.spacingHorizontalXXXL}))`,
        margin: "0 auto",

        [BREAKPOINT_MOBILE]: {
            maxWidth: WIDTH_CONTENT,
        },
    },

    introduction: {
        width: "100%",
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: SPACE_SM,
        textAlign: "left",
    },

    heading: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeHero800,
        lineHeight: tokens.lineHeightHero800,
        fontWeight: tokens.fontWeightSemibold,

        [BREAKPOINT_MOBILE]: {
            fontSize: tokens.fontSizeHero700,
            lineHeight: tokens.lineHeightHero700,
        },
    },

    description: {
        maxWidth: "43rem",
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,
    },

    tabViewport: {
        width: "100%",
        overflowX: "auto",
        paddingBottom: tokens.spacingVerticalXS,
        scrollbarWidth: "none",

        "&::-webkit-scrollbar": {
            display: "none",
        },
    },

    tabs: {
        display: "flex",
        justifyContent: "flex-start",
        minWidth: "max-content",
    },

    experience: {
        display: "grid",
        gridTemplateColumns: "minmax(12rem, 15rem) minmax(0, 1fr)",
        alignItems: "stretch",
        gap: tokens.spacingHorizontalL,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "minmax(0, 1fr)",
            gap: tokens.spacingVerticalL,
        },
    },

    summary: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        justifyContent: "center",
        gap: tokens.spacingVerticalL,
        padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalXL}`,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusXLarge,
        backgroundImage: `linear-gradient(145deg, ${tokens.colorBrandBackground2} 0%, ${tokens.colorNeutralBackground1} 100%)`,

        [BREAKPOINT_MOBILE]: {
            gap: tokens.spacingVerticalM,
            padding: tokens.spacingVerticalL,
        },
    },

    copy: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: tokens.spacingVerticalXS,
        minWidth: 0,
    },

    industryName: {
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase600,
        lineHeight: tokens.lineHeightBase600,
        fontWeight: tokens.fontWeightSemibold,
    },

    outcome: {
        color: tokens.colorNeutralForeground2,
        lineHeight: tokens.lineHeightBase500,
    },

    action: {
        flexShrink: 0,
    },
});
