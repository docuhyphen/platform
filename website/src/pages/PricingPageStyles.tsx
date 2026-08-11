import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, SPACE_LG, SPACE_MD, SPACE_SM, SPACE_XL, WIDTH_SUBTITLE} from "../landing/shared.ts";

export const usePricingPageStyles = makeStyles({
    page: {
        display: "flex",
        flexDirection: "column",
        alignItems: "stretch",
        gap: SPACE_XL,
        width: "100%",
    },

    introduction: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: SPACE_SM,
        width: "100%",
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
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,
    },

    billingOptions: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        justifyContent: "flex-start",
        gap: SPACE_SM,
        marginTop: SPACE_MD,
    },

    comparison: {
        overflow: "hidden",
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusXLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow16,
    },

    tableViewport: {
        width: "100%",
        overflowX: "auto",
    },

    table: {
        minWidth: "64rem",
        tableLayout: "fixed",

        "& th, & td": {
            padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalL}`,
            borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
            verticalAlign: "middle",
        },

        "& th:not(:first-child), & td:not(:first-child)": {
            borderLeft: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
            textAlign: "center",
        },

        "& tbody tr:nth-child(even)": {
            backgroundColor: tokens.colorNeutralBackground2,
        },

        "& tbody tr:last-child th, & tbody tr:last-child td": {
            borderBottom: 0,
        },

        [BREAKPOINT_MOBILE]: {
            minWidth: "46rem",
        },
    },

    featureHeader: {
        width: "25%",
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.06em",
        textTransform: "uppercase",

        [BREAKPOINT_MOBILE]: {
            width: "22%",
        },
    },

    featureHeaderContent: {
        textAlign: "center",
        width: "100%",
        gap: tokens.spacingVerticalL,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "space-between",
    },

    featureAction: {
        width: "100%",
    },

    planHeader: {
        verticalAlign: "top",
        backgroundImage: `linear-gradient(180deg, ${tokens.colorBrandBackground2} 0%, ${tokens.colorNeutralBackground1} 100%)`,
    },

    planHeading: {
        display: "flex",
        flexDirection: "column",
        alignItems: "stretch",
        gap: SPACE_SM,
        minHeight: "17rem",
    },

    planNameRow: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        justifyContent: "center",
        gap: tokens.spacingHorizontalS,
    },

    planName: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeBase600,
        lineHeight: tokens.lineHeightBase600,
        fontWeight: tokens.fontWeightSemibold,
    },

    planDescription: {
        minHeight: "3.9rem",
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase300,
        lineHeight: tokens.lineHeightBase400,
    },

    priceRow: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "baseline",
        justifyContent: "center",
        gap: tokens.spacingHorizontalXS,
    },

    price: {
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeHero800,
        lineHeight: tokens.lineHeightHero800,
        fontWeight: tokens.fontWeightSemibold,
    },

    cadence: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
    },

    billingDetail: {
        minHeight: "2.5rem",
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase200,
        lineHeight: tokens.lineHeightBase300,
    },

    featureCell: {
        position: "sticky",
        left: 0,
        zIndex: 1,
        width: "25%",
        backgroundColor: tokens.colorNeutralBackground1,
        color: tokens.colorNeutralForeground1,
        fontWeight: tokens.fontWeightSemibold,
        textAlign: "left",

        [BREAKPOINT_MOBILE]: {
            width: "22%",
        },
    },

    /**
     * A capability that is planned but not sold yet. Deliberately quieter than an included
     * feature so it can never be mistaken for something the plan entitles you to today.
     */
    pendingValue: {
        color: tokens.colorNeutralForeground3,
        fontStyle: "italic",
    },

    notes: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(16rem, 1fr))",
        gap: SPACE_LG,
        padding: SPACE_LG,
        color: tokens.colorNeutralForeground2,
        backgroundColor: tokens.colorNeutralBackground2,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "minmax(0, 1fr)",
            gap: SPACE_MD,
        },
    },
});
