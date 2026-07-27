import {makeStyles, tokens} from '@fluentui/react-components';

export const useExchangeAcceptanceDialogStyles = makeStyles({

    overlay: {
        position: "absolute",
        inset: 0,
        zIndex: 10,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        backgroundColor: tokens.colorBackgroundOverlay,
        backdropFilter: "blur(4px)",
        borderRadius: tokens.borderRadiusXLarge,
    },

    overlayCard: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        padding: `${tokens.spacingVerticalXL} ${tokens.spacingHorizontalXXL}`,
        maxWidth: "580px",
        width: "100%",
        boxShadow: tokens.shadow28,
        borderRadius: tokens.borderRadiusXLarge,
    },

    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL
    },
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
    },

    sectionLabel: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalSNudge,
    },

    sectionIcon: {
        fontSize: "14px",
        color: tokens.colorNeutralForeground3,
        flexShrink: 0,
    },

    labelText: {
        color: tokens.colorNeutralForeground3,
    },

    sectionContent: {
        paddingLeft: tokens.spacingHorizontalXL,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },

    requesterCaption: {
        color: tokens.colorNeutralForeground2,
        marginTop: tokens.spacingVerticalXXS,
    },

    messageBox: {
        backgroundColor: tokens.colorNeutralBackground3,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        borderLeftWidth: "3px",
        borderLeftStyle: "solid",
        borderLeftColor: tokens.colorBrandBackground,
    },

    documentList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },

    documentItem: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },

    documentIcon: {
        fontSize: "14px",
        color: tokens.colorNeutralForeground3,
        flexShrink: 0,
    },

    emptyText: {
        color: tokens.colorNeutralForeground3,
    },

    alreadySharedLabel: {
        color: tokens.colorNeutralForeground2,
        marginTop: tokens.spacingVerticalSNudge,
    },

    declineFieldContainer: {
        width: "100%",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
    },

    navigationActions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },

    actions: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },

    primaryActions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        marginLeft: "auto",
    },

    tertiaryActions: {
        display: "flex",
        justifyContent: "flex-start",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
