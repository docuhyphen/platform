import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeOverviewStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
        position: "sticky",
        top: "88px",
        padding: tokens.spacingHorizontalXXL,
        color: tokens.colorNeutralForeground1,
        background: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        boxShadow: tokens.shadow16,
        overflow: "hidden",
        transitionProperty: "padding, gap, box-shadow",
        transitionDuration: tokens.durationNormal,
        zIndex: 2,
        '@media (max-width: 920px)': {
            top: "72px",
            width: "100%",
            boxSizing: "border-box"
        },
        '@media (max-width: 640px)': {
            padding: tokens.spacingHorizontalL,
            borderRadius: tokens.borderRadiusLarge,
            width: "100%",
            boxSizing: "border-box"
        },
    },

    collapsedContainer: {
        gap: 0,
        paddingTop: tokens.spacingVerticalM,
        paddingBottom: tokens.spacingVerticalM,
        boxShadow: tokens.shadow8,
    },

    summaryHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
    },

    eyebrow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        color: tokens.colorBrandForeground1,
        textTransform: "uppercase",
        letterSpacing: "0.08em",
        fontSize: "12px",
    },

    details: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
    },

    heading: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
    },

    requestedBy: {
        color: tokens.colorNeutralForeground3,
    },

    exchangeName: {
        color: tokens.colorNeutralForeground1,
        lineHeight: "1.15",
        overflowWrap: "anywhere",
    },

    recipientOrganization: {
        color: tokens.colorBrandForeground1,
    },

    message: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        padding: tokens.spacingHorizontalL,
        background: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusLarge,
    },

    messageLabel: {
        color: tokens.colorNeutralForeground3,
        fontSize: "12px",
        fontWeight: 600,
    },

    messageText: {
        color: tokens.colorNeutralForeground1,
        lineHeight: "1.5",
        overflowWrap: "anywhere",
    },

    assuranceList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    },

    assuranceItem: {
        display: "flex",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalM,
    },

    assuranceIcon: {
        flexShrink: 0,
        width: "20px",
        height: "20px",
        color: tokens.colorBrandForeground1,
    },

    assuranceText: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        color: tokens.colorNeutralForeground2,
        lineHeight: "1.4",
    },

    footerNote: {
        paddingTop: tokens.spacingVerticalL,
        color: tokens.colorNeutralForeground3,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        fontSize: "12px",
        lineHeight: "1.5",
    },
});
