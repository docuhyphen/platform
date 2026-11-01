import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SUBHEADING_FONT_WEIGHT,
    WIDTH_CONTENT,
    WIDTH_SUBTITLE,
} from "../shared.ts";

export const usePricingTeaserSectionStyles = makeStyles({
    wrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        backgroundColor: tokens.colorNeutralBackground1,
        borderTop: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    container: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
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

    grid: {
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_MD,
        },
    },
});
