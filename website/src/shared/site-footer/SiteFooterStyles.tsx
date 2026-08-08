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
        whiteSpace: "nowrap",
        [BREAKPOINT_MOBILE]: {
            whiteSpace: "normal",
        },
    },
});
