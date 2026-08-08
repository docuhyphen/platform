import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XL,
    WIDTH_CONTENT,
    WIDTH_SUBTITLE,
} from "../shared.ts";

export const useContactInfoSectionStyles = makeStyles({
    wrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",

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

    sectionTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeHero800,
    },

    subheading: {
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForeground1,
        fontWeight: "100",
    },

    grid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: SPACE_LG,
        alignItems: "stretch",
        maxWidth: WIDTH_CONTENT,
        marginLeft: "auto",
        marginRight: "auto",

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
        },
    },

    cardsColumn: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    card: {
        backgroundColor: tokens.colorNeutralBackground1,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_MD,
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: SPACE_SM,
    },

    cardHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
    },

    cardContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        minWidth: 0,
    },

    cardTitle: {
        fontSize: tokens.fontSizeBase300,
        fontWeight: tokens.fontWeightSemibold,
        lineHeight: tokens.lineHeightBase300,
    },

    icon: {
        flexShrink: 0,
        width: "2rem",
        height: "2rem",
        borderRadius: "0.5rem",
        backgroundColor: tokens.colorBrandBackground2,
        color: tokens.colorBrandForeground1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
    },

    link: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
        textDecorationLine: "none",
    },
});










