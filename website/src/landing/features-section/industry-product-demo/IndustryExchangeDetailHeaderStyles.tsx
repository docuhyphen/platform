import {makeStyles, tokens} from "@fluentui/react-components";

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
    },

    actions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexShrink: 0,
    },

    actionButton: {
        flexShrink: 0,
    },

    wideActionButton: {
        flexShrink: 0,
    },
});
