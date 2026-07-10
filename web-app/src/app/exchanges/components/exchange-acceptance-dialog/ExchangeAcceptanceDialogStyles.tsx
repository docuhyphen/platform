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
        gap: "12px",
        padding: "20px 24px",
        maxWidth: "580px",
        width: "100%",
        boxShadow: tokens.shadow28,
        borderRadius: tokens.borderRadiusXLarge,
    },

    section: {
        display: "flex",
        flexDirection: "column",
        gap: "6px",
    },

    sectionLabel: {
        display: "flex",
        alignItems: "center",
        gap: "6px",
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
        paddingLeft: "20px",
        display: "flex",
        flexDirection: "column",
        gap: "4px",
    },

    requesterCaption: {
        color: tokens.colorNeutralForeground2,
        marginTop: "2px",
    },

    messageBox: {
        backgroundColor: tokens.colorNeutralBackground3,
        borderRadius: tokens.borderRadiusMedium,
        padding: "8px 12px",
        borderLeftWidth: "3px",
        borderLeftStyle: "solid",
        borderLeftColor: tokens.colorBrandBackground,
    },

    documentList: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
    },

    documentItem: {
        display: "flex",
        alignItems: "center",
        gap: "8px",
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
        marginTop: "6px",
    },

    declineFieldContainer: {
        width: "100%",
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },

    navigationActions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: "8px",
        flexWrap: "wrap",
    },

    actions: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "8px",
        flexWrap: "wrap",
    },

    primaryActions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: "8px",
        flexWrap: "wrap",
        marginLeft: "auto",
    },

    tertiaryActions: {
        display: "flex",
        justifyContent: "flex-start",
        gap: "8px",
        flexWrap: "wrap",
    },
});
