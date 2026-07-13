import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE} from "../../shared.ts";

export const useIndustryExchangeDetailHeaderStyles = makeStyles({
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
        margin: tokens.spacingVerticalS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalL}`,
        borderTop: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRight: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderLeft: `0.3125rem solid ${tokens.colorPaletteBlueForeground2}`,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow4,

        [BREAKPOINT_MOBILE]: {
            gap: tokens.spacingHorizontalXS,
            padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalS}`,
        },
    },

    title: {
        overflow: "hidden",
        minWidth: 0,
        flex: 1,
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase600,
        lineHeight: tokens.lineHeightBase600,
        fontWeight: tokens.fontWeightRegular,
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",

        [BREAKPOINT_MOBILE]: {
            fontSize: tokens.fontSizeBase400,
            lineHeight: tokens.lineHeightBase400,
        },

        "@media (max-width: 30em)": {
            fontSize: tokens.fontSizeBase300,
            lineHeight: tokens.lineHeightBase300,
        },
    },

    actions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexShrink: 0,

        [BREAKPOINT_MOBILE]: {
            gap: tokens.spacingHorizontalXXS,
        },
    },

    actionButton: {
        flexShrink: 0,
    },

    wideActionButton: {
        flexShrink: 0,

        "@media (max-width: 64em)": {
            display: "none",
        },
    },
});
