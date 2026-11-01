import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE} from "../../../shared.ts";

export const useIndustryExperienceSummaryStyles = makeStyles({
    summary: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        justifyContent: "center",
        gap: tokens.spacingVerticalL,
        padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalXL}`,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusXLarge,
        backgroundImage: `linear-gradient(145deg, ${tokens.colorBrandBackground2} 0%, ${tokens.colorNeutralBackground1} 100%)`,

        [BREAKPOINT_MOBILE]: {
            gap: tokens.spacingVerticalM,
            padding: tokens.spacingVerticalL,
        },
    },

    copy: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: tokens.spacingVerticalXS,
        minWidth: 0,
    },

    industryName: {
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase600,
        lineHeight: tokens.lineHeightBase600,
        fontWeight: tokens.fontWeightSemibold,
    },

    outcome: {
        color: tokens.colorNeutralForeground2,
        lineHeight: tokens.lineHeightBase500,
    },

    action: {
        flexShrink: 0,
    },
});
