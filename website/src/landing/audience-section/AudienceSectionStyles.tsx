import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    LAW_SECTION_GRADIENT,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SECTION_TOP_RADIUS,
    SPACE_LG,
    SPACE_SM,
    SPACE_XL,
    SPACE_XS,
    SUBHEADING_FONT_WEIGHT,
    WIDTH_CONTENT,
    WIDTH_SUBTITLE,
} from "../shared.ts";

export const useAudienceSectionStyles = makeStyles({
    wrapper: {
        color: tokens.colorNeutralForegroundOnBrand,
        background: LAW_SECTION_GRADIENT,
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        borderTopLeftRadius: SECTION_TOP_RADIUS,
        borderTopRightRadius: SECTION_TOP_RADIUS,

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        marginBottom: SPACE_XL,
        alignItems: "flex-start",
    },

    sectionTitle: {
        color: tokens.colorNeutralForegroundOnBrand,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeHero800,
    },

    container: {
        boxSizing: "border-box",
        margin: "0 auto",
        maxWidth: WIDTH_CONTENT,
        width: "100%",
        color: tokens.colorNeutralForegroundOnBrand,
        backgroundColor: "transparent",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
        padding: 0,

        [BREAKPOINT_MOBILE]: {
            padding: 0,
            margin: "0",
        },
    },

    subheading: {
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForegroundOnBrand,
        fontWeight: SUBHEADING_FONT_WEIGHT,
    },

    accordionHeader: {
        color: tokens.colorNeutralForegroundOnBrand,
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,
        fontWeight: tokens.fontWeightSemibold,
        marginTop: tokens.spacingVerticalXL,
    },

    accordionPanel: {
        color: tokens.colorNeutralForegroundOnBrand,
    },

    list: {
        border: `${tokens.strokeWidthThicker} dotted ${tokens.colorNeutralStrokeAlpha2}`,
        borderRadius: CARD_RADIUS,
        padding: tokens.spacingVerticalM,
        marginTop: SPACE_XS,
        paddingLeft: SPACE_SM,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,

        "& li": {
            display: "flex",
            flexDirection: "column",
            gap: tokens.spacingVerticalXS,
        },
    },
});
