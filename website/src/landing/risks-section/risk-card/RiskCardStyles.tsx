import {makeStyles, tokens} from "@fluentui/react-components";
import {CARD_RADIUS, SPACE_LG, SPACE_SM, SPACE_XS} from "../../shared.ts";

const ACCENT_BORDER_WIDTH = "0.4rem";

const accentGradient = (start: string, end: string) =>
    `linear-gradient(135deg, color-mix(in srgb, ${start} 35%, transparent) 0%, color-mix(in srgb, ${end} 15%, transparent) 100%)`;

export const useRiskCardStyles = makeStyles({
    card: {
        borderRadius: CARD_RADIUS,
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
        boxShadow: tokens.shadow4,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        transitionProperty: "transform, box-shadow",
        transitionDuration: tokens.durationNormal,
        transitionTimingFunction: "ease",

        ":hover": {
            transform: "translateY(-2px)",
            boxShadow: tokens.shadow8,
        },
    },

    card1: {
        backgroundImage: accentGradient(tokens.colorPaletteBlueBackground2, tokens.colorPaletteBlueBorderActive),
        borderTop: `${ACCENT_BORDER_WIDTH} solid ${tokens.colorPaletteBlueBorderActive}`,
    },

    card2: {
        backgroundImage: accentGradient(tokens.colorPaletteLavenderBackground2, tokens.colorPaletteLavenderBorderActive),
        borderTop: `${ACCENT_BORDER_WIDTH} solid ${tokens.colorPaletteLavenderBorderActive}`,
    },

    card3: {
        backgroundImage: accentGradient(tokens.colorPaletteTealBackground2, tokens.colorPaletteTealBorderActive),
        borderTop: `${ACCENT_BORDER_WIDTH} solid ${tokens.colorPaletteTealBorderActive}`,
    },

    card4: {
        backgroundImage: accentGradient(tokens.colorPaletteGoldBackground2, tokens.colorPaletteGoldBorderActive),
        borderTop: `${ACCENT_BORDER_WIDTH} solid ${tokens.colorPaletteGoldBorderActive}`,
    },

    cardContent: {
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    cardSubtitle: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_XS,
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase400,
        lineHeight: tokens.lineHeightBase400,
        fontWeight: tokens.fontWeightSemibold,
    },

    cardTitle: {
        display: "flex",
        minHeight: "2.5em",
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,
    },

    readMoreButton: {
        alignSelf: "flex-start",
        padding: 0,
        minWidth: "unset",
        color: tokens.colorBrandForeground1,
    },

    cardParagraph: {
        marginTop: SPACE_XS,
    },
});
