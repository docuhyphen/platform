import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, CARD_RADIUS, SPACE_LG, SPACE_MD, SPACE_SM, SPACE_XS} from "../landing/shared.ts";

export const usePricingPageStyles = makeStyles({
    page: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: SPACE_LG,
    },

    hero: {
        width: "100%",
        maxWidth: "52rem",
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        textAlign: "center",
        gap: SPACE_SM,
        padding: "4rem 2rem",
        backgroundColor: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,

        [BREAKPOINT_MOBILE]: {
            padding: `${SPACE_LG} ${SPACE_MD}`,
        },
    },

    heroIcon: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "3rem",
        height: "3rem",
        color: tokens.colorBrandForeground1,
        backgroundColor: tokens.colorBrandBackground2,
        borderRadius: "50%",
    },

    eyebrow: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeBase300,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.04em",
        textTransform: "uppercase",
    },

    title: {
        color: tokens.colorNeutralForeground1,
        fontWeight: tokens.fontWeightSemibold,
        maxWidth: "42rem",
    },

    description: {
        color: tokens.colorNeutralForeground2,
        maxWidth: "40rem",
    },

    actions: {
        display: "flex",
        flexWrap: "wrap",
        justifyContent: "center",
        gap: SPACE_SM,
        marginTop: SPACE_XS,
    },

    nextSteps: {
        width: "100%",
        maxWidth: "52rem",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    nextStepsLabel: {
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase300,
        fontWeight: tokens.fontWeightSemibold,
    },

    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
        gap: SPACE_MD,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
        },
    },

    card: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: SPACE_SM,
        padding: SPACE_LG,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
    },

    cardIcon: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "2.5rem",
        height: "2.5rem",
        color: tokens.colorBrandForeground1,
        backgroundColor: tokens.colorBrandBackground2,
        borderRadius: "50%",
    },

    cardLink: {
        display: "inline-flex",
        alignItems: "center",
        gap: SPACE_XS,
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
        textDecorationLine: "none",

        ":hover": {
            textDecorationLine: "underline",
        },
    },
});
