import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XS,
} from "../landing/shared.ts";

export const useIndustriesPageStyles = makeStyles({
    hero: {
        display: "grid",
        gridTemplateColumns: "minmax(18rem, 0.72fr) minmax(0, 1fr)",
        alignItems: "center",
        gap: SPACE_LG,
        padding: "2.5rem",
        overflow: "hidden",
        borderRadius: CARD_RADIUS,
        color: tokens.colorNeutralForegroundInverted,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            padding: SPACE_LG,
        },
    },

    heroRealEstate: {
        backgroundImage: "linear-gradient(135deg, #2d3953 0%, #4b678c 52%, #99b1cf 100%)",
    },

    heroLegal: {
        backgroundImage: "linear-gradient(135deg, #20344d 0%, #385980 55%, #7ea6d6 100%)",
    },

    heroHealthcare: {
        backgroundImage: "linear-gradient(135deg, #24505a 0%, #3f8391 52%, #8fd0dc 100%)",
    },

    heroAccounting: {
        backgroundImage: "linear-gradient(135deg, #2e3558 0%, #5c6da9 50%, #a2b5e8 100%)",
    },

    heroBanking: {
        backgroundImage: "linear-gradient(135deg, #243f63 0%, #3f6ba1 54%, #84b5ea 100%)",
    },

    heroText: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    heroEyebrow: {
        color: "rgba(255,255,255,0.85)",
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.08em",
        textTransform: "uppercase",
    },

    heroTitle: {
        color: tokens.colorNeutralForegroundInverted,
    },

    heroBlurb: {
        color: "rgba(255,255,255,0.9)",
    },

    heroActions: {
        display: "flex",
        gap: SPACE_SM,
        marginTop: SPACE_SM,
        flexWrap: "wrap",
    },

    heroDemo: {
        minWidth: 0,
    },

    grid2: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: SPACE_LG,
        marginTop: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
        },
    },

    panel: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        padding: SPACE_LG,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        backgroundColor: tokens.colorNeutralBackground1,
    },

    panelTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    useCase: {
        paddingTop: SPACE_SM,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    bulletList: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_XS,
        margin: 0,
        padding: 0,
        listStyle: "none",
    },

    bullet: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_XS,
    },

    bulletIcon: {
        color: tokens.colorBrandForeground1,
    },

    finalCta: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_MD,
        marginTop: SPACE_LG,
        flexWrap: "wrap",
    },
});
