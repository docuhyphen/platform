import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XS,
} from "../../landing/shared.ts";

export const useSecurityPageStyles = makeStyles({
    hero: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        textAlign: "center",
        alignItems: "center",
    },
    heroTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },
    heroBlurb: {
        maxWidth: "42rem",
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
    pillar: {
        backgroundColor: tokens.colorNeutralBackground1,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },
    pillarIcon: {
        width: "3rem",
        height: "3rem",
        borderRadius: tokens.borderRadiusXLarge,
        backgroundColor: tokens.colorBrandBackground2,
        color: tokens.colorBrandForeground1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
    },
    band: {
        marginTop: SPACE_LG,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_MD,
    },
    list: {
        listStyle: "none",
        margin: 0,
        padding: 0,
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: SPACE_SM,
        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
        },
    },
    listItem: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_XS,
        padding: SPACE_SM,
        borderLeft: `${tokens.strokeWidthThicker} solid ${tokens.colorBrandStroke1}`,
        backgroundColor: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusLarge,
    },
    cta: {
        marginTop: SPACE_LG,
        display: "flex",
        gap: SPACE_MD,
        flexWrap: "wrap",
    },
});

