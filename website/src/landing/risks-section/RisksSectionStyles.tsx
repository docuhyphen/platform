import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XL,
    SUBHEADING_FONT_WEIGHT,
    WIDTH_CONTENT,
    WIDTH_SUBTITLE,
} from "../shared.ts";

export const useRisksSectionStyles = makeStyles({
    sectionWrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        backgroundColor: tokens.colorNeutralBackground2,
        backgroundImage: "url('/backgrounds/leaky-file-network.svg')",
        backgroundRepeat: "no-repeat",
        backgroundSize: "cover",
        backgroundPosition: "center",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    introWrapper: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        marginBottom: SPACE_XL,
        alignItems: "flex-start",
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: SPACE_SM,

        [BREAKPOINT_MOBILE]: {
            gap: SPACE_SM,
        },
    },

    cardsWrapper: {
        boxSizing: "border-box",
        background: "transparent",
        marginTop: 0,
    },

    cardsContainer: {
        display: "grid",
        gap: SPACE_LG,
        gridTemplateColumns: "repeat(2, 1fr)",
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_MD,
        },
    },

    sectionTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeHero800,
    },

    subheading: {
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForeground1,
        fontWeight: SUBHEADING_FONT_WEIGHT,
    },
});
