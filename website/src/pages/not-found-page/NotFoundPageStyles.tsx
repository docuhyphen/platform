import {makeStyles, tokens} from "@fluentui/react-components";
import {SPACE_MD, SPACE_SM} from "../../landing/shared.ts";

export const useNotFoundPageStyles = makeStyles({
    wrapper: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        textAlign: "center",
        gap: SPACE_SM,
        padding: `calc(${tokens.spacingVerticalXXXL} * 2) ${tokens.spacingHorizontalL}`,
    },

    code: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeHero1000,
        fontWeight: tokens.fontWeightSemibold,
        lineHeight: tokens.lineHeightHero1000,
    },

    title: {
        color: tokens.colorNeutralForeground1,
    },

    blurb: {
        color: tokens.colorNeutralForeground2,
        maxWidth: "32rem",
    },

    actions: {
        display: "flex",
        gap: SPACE_MD,
        flexWrap: "wrap",
        justifyContent: "center",
        marginTop: SPACE_MD,
    },
});
