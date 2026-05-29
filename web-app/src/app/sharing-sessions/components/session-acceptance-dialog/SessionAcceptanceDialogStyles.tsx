import {makeStyles, tokens} from '@fluentui/react-components';

export const useSessionAcceptanceDialogStyles = makeStyles({

    // Blocking frosted-glass overlay covering the entire details panel.
    overlay: {
        position: "absolute",
        inset: 0,
        zIndex: 10,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        backgroundColor: "rgba(255, 255, 255, 0.88)",
        backdropFilter: "blur(4px)",
        borderRadius: "8px",
    },

    overlayCard: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        padding: "32px",
        maxWidth: "420px",
        width: "90%",
        boxShadow: tokens.shadow28,
        borderRadius: "8px",
    },

    sessionNameContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "2px",
    },

    participantInfoContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "2px",
    },

    messageContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "2px",
    },

    documentsInfoContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "6px",
    },

    documentsGroup: {
        display: "flex",
        flexDirection: "column",
        gap: "2px",
    },

    declineFieldContainer: {
        width: "100%",
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },


    actions: {
        display: "flex",
        gap: "8px",
        justifyContent: "center",
        flexWrap: "wrap",
        marginTop: "4px",
    },

    navigationHint: {
        color: tokens.colorNeutralForeground3,
    },
});
