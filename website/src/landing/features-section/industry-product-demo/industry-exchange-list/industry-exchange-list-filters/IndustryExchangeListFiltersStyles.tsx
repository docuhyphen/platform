import {makeStyles, tokens} from "@fluentui/react-components";

export const useIndustryExchangeListFiltersStyles = makeStyles({
    filters: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalSNudge,
        paddingTop: tokens.spacingVerticalS,
        paddingRight: tokens.spacingHorizontalS,
        paddingBottom: tokens.spacingVerticalS,
        paddingLeft: tokens.spacingHorizontalS,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },

    statuses: {
        width: "100%",
        paddingRight: 0,
        paddingLeft: 0,
    },

    tabContent: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalSNudge,
        whiteSpace: "nowrap",
    },

    activeTabContent: {
        color: tokens.colorBrandForeground1,
    },

    searchRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },

    search: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
        minHeight: "2rem",
        flex: 1,
        paddingRight: tokens.spacingHorizontalS,
        paddingLeft: tokens.spacingHorizontalS,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase100,
    },

    utilityIcon: {
        flexShrink: 0,
        color: tokens.colorNeutralForeground2,
    },
});
