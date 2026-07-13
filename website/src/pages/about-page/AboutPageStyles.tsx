import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
} from "../../landing/shared.ts";

export const useAboutPageStyles = makeStyles({
    hero: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1.05fr) minmax(22rem, 0.95fr)",
        alignItems: "center",
        gap: tokens.spacingHorizontalXXXL,
        paddingTop: tokens.spacingVerticalXXXL,
        paddingBottom: tokens.spacingVerticalXXXL,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "minmax(0, 1fr)",
            gap: tokens.spacingVerticalXXL,
            paddingTop: tokens.spacingVerticalXL,
            paddingBottom: tokens.spacingVerticalXL,
        },
    },

    heroCopy: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: SPACE_MD,
    },

    heroEyebrow: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.08em",
        textTransform: "uppercase",
    },

    heroTitle: {
        maxWidth: "38rem",
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    heroText: {
        maxWidth: "36rem",
        color: tokens.colorNeutralForeground2,
        lineHeight: tokens.lineHeightBase500,
    },

    visual: {
        position: "relative",
        minHeight: "22rem",
        display: "grid",
        placeItems: "center",
        borderRadius: tokens.borderRadiusXLarge,
        backgroundImage: `radial-gradient(circle, ${tokens.colorBrandBackground2} 0%, ${tokens.colorNeutralBackground1} 70%)`,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        overflow: "hidden",

        "::before": {
            content: "\"\"",
            position: "absolute",
            width: "70%",
            height: "70%",
            border: `${tokens.strokeWidthThin} solid ${tokens.colorBrandStroke2}`,
            borderRadius: tokens.borderRadiusCircular,
            opacity: 0.45,
        },

        [BREAKPOINT_MOBILE]: {
            minHeight: "20rem",
        },
    },

    visualCore: {
        position: "relative",
        zIndex: 2,
        width: "10rem",
        height: "10rem",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: SPACE_SM,
        padding: SPACE_MD,
        boxSizing: "border-box",
        textAlign: "center",
        borderRadius: tokens.borderRadiusCircular,
        color: tokens.colorNeutralForegroundOnBrand,
        backgroundColor: tokens.colorBrandBackground,
        boxShadow: tokens.shadow16,

        "& svg": {
            width: "2rem",
            height: "2rem",
        },
    },

    visualPill: {
        position: "absolute",
        zIndex: 3,
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        borderRadius: tokens.borderRadiusCircular,
        color: tokens.colorNeutralForeground2,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        boxShadow: tokens.shadow4,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,

        "& svg": {
            color: tokens.colorBrandForeground1,
        },
    },

    pillPeople: {top: "12%", left: "7%"},
    pillDocuments: {top: "12%", right: "7%"},
    pillWorkflows: {bottom: "12%", left: "7%"},
    pillAudit: {bottom: "12%", right: "7%"},

    section: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
        paddingTop: tokens.spacingVerticalXXXL,
        paddingBottom: tokens.spacingVerticalXXXL,
    },

    sectionHeading: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        maxWidth: "42rem",
    },

    sectionTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    sectionIntro: {
        color: tokens.colorNeutralForeground2,
    },

    principleGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
        gap: SPACE_MD,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "minmax(0, 1fr)",
        },
    },

    principleCard: {
        minHeight: "10rem",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        padding: SPACE_LG,
        borderRadius: CARD_RADIUS,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },

    iconWrap: {
        width: "2.5rem",
        height: "2.5rem",
        display: "grid",
        placeItems: "center",
        borderRadius: tokens.borderRadiusMedium,
        color: tokens.colorBrandForeground1,
        backgroundColor: tokens.colorBrandBackground2,
    },

    principleTitle: {
        color: tokens.colorNeutralForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    principleText: {
        color: tokens.colorNeutralForeground2,
    },

    statement: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1.2fr) minmax(18rem, 0.8fr)",
        gap: SPACE_LG,
        alignItems: "center",
        padding: tokens.spacingVerticalXXXL,
        borderRadius: tokens.borderRadiusXLarge,
        color: tokens.colorNeutralForegroundOnBrand,
        backgroundImage: `linear-gradient(135deg, ${tokens.colorBrandBackground} 0%, ${tokens.colorCompoundBrandBackgroundHover} 100%)`,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "minmax(0, 1fr)",
            padding: SPACE_LG,
        },
    },

    statementTitle: {
        color: tokens.colorNeutralForegroundOnBrand,
        fontWeight: tokens.fontWeightSemibold,
    },

    commitments: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    commitment: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        fontWeight: tokens.fontWeightSemibold,
    },

    cta: {
        display: "flex",
        gap: SPACE_MD,
        flexWrap: "wrap",
        paddingBottom: tokens.spacingVerticalXXXL,
    },
});
