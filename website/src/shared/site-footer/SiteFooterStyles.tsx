import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    WIDTH_CONTENT,
} from "../../landing/shared.ts";

export const useSiteFooterStyles = makeStyles({
    footer: {
        width: "100%",
        backgroundColor: tokens.colorNeutralBackground1,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
    },
    inner: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        minHeight: "9rem",
        padding: `${tokens.spacingVerticalXXL} 2rem`,
        boxSizing: "border-box",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalL,
        [BREAKPOINT_MOBILE]: {
            padding: `${tokens.spacingVerticalXXL} 1.6rem`,
            gap: tokens.spacingHorizontalL,
        },
    },
    logo: {
        display: "inline-flex",
        alignItems: "center",
        minWidth: 0,
    },
    copyright: {
        color: tokens.colorNeutralForeground3,
        textAlign: "right",
        [BREAKPOINT_MOBILE]: {
            textAlign: "left",
        },
    },
    meta: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-end",
        gap: tokens.spacingVerticalS,
        minWidth: 0,
        [BREAKPOINT_MOBILE]: {
            alignItems: "flex-start",
        },
    },
    legalLinks: {
        display: "flex",
        flexWrap: "wrap",
        justifyContent: "flex-end",
        columnGap: tokens.spacingHorizontalM,
        rowGap: tokens.spacingVerticalXS,
        [BREAKPOINT_MOBILE]: {
            justifyContent: "flex-start",
        },
    },
    legalLink: {
        color: tokens.colorBrandForegroundLink,
        fontSize: tokens.fontSizeBase200,
        textDecorationLine: "none",
        ":hover": {
            textDecorationLine: "underline",
        },
        ":focus-visible": {
            outlineStyle: "solid",
            outlineWidth: tokens.strokeWidthThick,
            outlineColor: tokens.colorStrokeFocus2,
            outlineOffset: "2px",
            borderRadius: tokens.borderRadiusMedium,
        },
    },
});
