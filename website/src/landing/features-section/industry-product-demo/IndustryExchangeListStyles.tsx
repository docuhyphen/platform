import {makeStyles, tokens} from "@fluentui/react-components";

export const useIndustryExchangeListStyles = makeStyles({
    sidebar: {
        display: "flex",
        flexDirection: "column",
        width: "25%",
        minWidth: "20rem",
        borderRight: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
    },

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

    exchangeExamples: {
        display: "flex",
        flexDirection: "column",
        flex: 1,
        minHeight: 0,
        overflow: "hidden",
    },

    exchange: {
        position: "relative",
        display: "grid",
        gridTemplateColumns: "2rem minmax(0, 1fr) auto",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minHeight: "4.35rem",
        paddingRight: tokens.spacingHorizontalS,
        paddingLeft: tokens.spacingHorizontalS,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
    },

    selectedExchange: {
        backgroundColor: tokens.colorNeutralBackground2,

        ":before": {
            position: "absolute",
            top: 0,
            bottom: 0,
            left: 0,
            width: "0.2rem",
            backgroundColor: tokens.colorBrandBackground,
            content: '""',
        },
    },

    exchangeAvatar: {
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        width: "2rem",
        height: "2rem",
        borderRadius: tokens.borderRadiusCircular,
        color: tokens.colorNeutralForeground3,
        backgroundColor: tokens.colorNeutralBackground3,
    },

    exchangeCopy: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: 0,
    },

    exchangeTitle: {
        overflow: "hidden",
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase100,
        fontWeight: tokens.fontWeightSemibold,
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
    },

    exchangeSummary: {
        overflow: "hidden",
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase100,
        fontStyle: "italic",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
    },

    updated: {
        alignSelf: "start",
        paddingTop: tokens.spacingVerticalM,
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase100,
        whiteSpace: "nowrap",
    },

    footer: {
        display: "flex",
        alignItems: "center",
        minHeight: "2.5rem",
        paddingLeft: tokens.spacingHorizontalM,
        borderTop: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase400,
    },
});
