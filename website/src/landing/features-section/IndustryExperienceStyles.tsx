import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, WIDTH_CONTENT} from "../shared.ts";

export const useIndustryExperienceStyles = makeStyles({
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXL,
        width: "100%",
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
    },

    introduction: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: tokens.spacingVerticalS,
        textAlign: "left",
    },

    heading: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeHero700,
        lineHeight: tokens.lineHeightHero700,
        fontWeight: tokens.fontWeightSemibold,

        [BREAKPOINT_MOBILE]: {
            fontSize: tokens.fontSizeBase600,
            lineHeight: tokens.lineHeightBase600,
        },
    },

    description: {
        maxWidth: "43rem",
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase400,
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
        gridTemplateColumns: "minmax(14rem, 0.38fr) minmax(0, 1fr)",
        overflow: "hidden",
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusXLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow16,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "minmax(0, 1fr)",
        },
    },

    copy: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        justifyContent: "center",
        gap: tokens.spacingVerticalM,
        padding: tokens.spacingVerticalXXXL,
        backgroundImage: `linear-gradient(145deg, ${tokens.colorBrandBackground2} 0%, ${tokens.colorNeutralBackground1} 100%)`,

        [BREAKPOINT_MOBILE]: {
            padding: tokens.spacingVerticalXL,
        },
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
        marginTop: tokens.spacingVerticalS,
    },

    browser: {
        minWidth: 0,
        padding: tokens.spacingVerticalL,
        backgroundColor: tokens.colorNeutralBackground2,

        [BREAKPOINT_MOBILE]: {
            padding: tokens.spacingVerticalM,
        },
    },

    browserFrame: {
        overflow: "hidden",
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow8,
    },

    browserBar: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        minHeight: "2.25rem",
        paddingLeft: tokens.spacingHorizontalM,
        paddingRight: tokens.spacingHorizontalM,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground3,
    },

    browserDot: {
        width: "0.5rem",
        height: "0.5rem",
        borderRadius: tokens.borderRadiusCircular,
        backgroundColor: tokens.colorNeutralStroke1,
    },

    browserDotBrand: {
        backgroundColor: tokens.colorBrandBackground,
    },

    browserDotSuccess: {
        backgroundColor: tokens.colorStatusSuccessBackground3,
    },

    image: {
        display: "block",
        width: "100%",
        aspectRatio: "1300 / 700",
        objectFit: "contain",
        backgroundColor: tokens.colorNeutralBackground1,
    },
});
