import {makeStyles, tokens} from "@fluentui/react-components";
import {CARD_RADIUS, SPACE_LG, SPACE_MD, SPACE_SM, SPACE_XS} from "../../shared.ts";

export const usePricingTeaserCardStyles = makeStyles({
    card: {
        position: "relative",
        backgroundColor: tokens.colorNeutralBackground1,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    cardFeatured: {
        border: `${tokens.strokeWidthThick} solid ${tokens.colorBrandStroke1}`,
        boxShadow: tokens.shadow16,
    },

    tierName: {
        fontSize: tokens.fontSizeBase500,
        fontWeight: tokens.fontWeightSemibold,
    },

    bestFor: {
        color: tokens.colorNeutralForeground1,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeBase200,
    },

    metaRow: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_XS,
    },

    metaIcon: {
        width: "1rem",
        height: "1rem",
        borderRadius: tokens.borderRadiusCircular,
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: tokens.fontSizeBase100,
        fontWeight: tokens.fontWeightSemibold,
        flexShrink: 0,
        backgroundColor: tokens.colorBrandBackground2,
        color: tokens.colorBrandForeground1,
    },

    priceRow: {
        display: "flex",
        alignItems: "baseline",
        gap: SPACE_XS,
    },

    price: {
        fontSize: tokens.fontSizeHero800,
        lineHeight: tokens.lineHeightHero800,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground1,
    },

    cadence: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
    },

    blurb: {
        color: tokens.colorNeutralForeground2,
        marginBottom: SPACE_SM,
    },

    bullets: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_XS,
        flex: 1,
        margin: 0,
        padding: 0,
        listStyle: "none",
    },

    bullet: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_XS,
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase300,
    },

    bulletBadge: {
        flexShrink: 0,
        width: "0.65rem",
        height: "0.65rem",
        minWidth: "0.65rem",
        minHeight: "0.65rem",
        padding: 0,
        borderRadius: tokens.borderRadiusCircular,
    },

    cta: {
        marginTop: SPACE_MD,
    },

    featuredBadge: {
        position: "absolute",
        top: `calc(-1 * ${tokens.spacingVerticalMNudge})`,
        left: SPACE_LG,
        background: tokens.colorBrandBackground,
        color: tokens.colorNeutralForegroundOnBrand,
        padding: `${tokens.spacingVerticalXXS} ${tokens.spacingHorizontalMNudge}`,
        borderRadius: tokens.borderRadiusCircular,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        textTransform: "uppercase",
        letterSpacing: "0.05em",
    },
});
