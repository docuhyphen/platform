import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeDocumentListStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        gap: "16px",
    },

    documentContainer: {
        background: tokens.colorNeutralBackground1,
    },

    verificationPanel: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        background: tokens.colorNeutralBackground1,
        padding: "12px",
        borderRadius: tokens.borderRadiusLarge,
    },

    accessWindowHint: {
        color: tokens.colorNeutralForeground3,
    },

    verificationControls: {
        display: "flex",
        gap: "8px",
        alignItems: "flex-end",
        flexWrap: "wrap",
    },

    otpInputField: {
        minWidth: "220px",
        flex: "1 1 220px",
    },

    documentCard: {
        width: "100%",
        padding: "16px",
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        '@media (max-width: 640px)': {
            padding: "12px",
        },
        '@media (max-width: 390px)': {
            padding: "10px",
        },
    },

    documentError: {
        marginBottom: "10px",
        whiteSpace: "normal",
        overflowWrap: "anywhere",
        minWidth: 0,
        maxWidth: "100%",
        overflow: "hidden",
    },

    documentErrorBody: {
        whiteSpace: "pre-wrap",
        overflowWrap: "anywhere",
        wordBreak: "break-word",
        maxWidth: "100%",
        minWidth: 0,
    },

    documentCardHeader: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        alignItems: "normal",
    },

    documentName: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flex: 1,
        gap: "8px",
        flexWrap: "wrap",
        '@media (max-width: 640px)': {
            alignItems: "flex-start",
        },
        '@media (max-width: 390px)': {
            gap: "4px",
        },
    },

    documentTitle: {
        overflowWrap: "anywhere",
        lineHeight: "1.3",
    },

    uploadedDate: {
        color: tokens.colorNeutralForeground3,
        lineHeight: "1.3",
        overflowWrap: "anywhere",
        '@media (max-width: 390px)': {
            fontSize: "12px",
        },
    },

    documentActions: {
        display: "flex",
        gap: "8px",
        flexDirection: "column",
    },

    documentActionsLine1: {
        display: "flex",
        gap: "12px",
        justifyContent: "space-between",
        alignItems: "flex-start",
        flexWrap: "wrap",
        '@media (max-width: 640px)': {
            flexDirection: "column",
            alignItems: "stretch",
        },
    },

    uploadActions: {
        display: "flex",
        gap: "8px",
        flexWrap: "wrap",
        '@media (max-width: 640px)': {
            width: "100%",
            flexDirection: "column",
        },
        '@media (max-width: 390px)': {
            gap: "6px",
        },
    },

    actionButton: {
        '@media (max-width: 640px)': {
            width: "100%",
            justifyContent: "center",
            minHeight: "40px",
        },
        '@media (max-width: 390px)': {
            minHeight: "36px",
            fontSize: "12px",
        },
    },

    downloadAction: {
        '@media (max-width: 640px)': {
            width: "100%",
        },
    },

    fileNameText: {
        overflowWrap: "anywhere",
    },

    documentActionsLine2: {
        '@media (max-width: 390px)': {
            marginTop: "2px",
        },
    },

    uploadButton1: {
        position: "relative",
    },

    uploadButton2: {
        position: "absolute",
        opacity: 0,
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        cursor: "pointer",
    }

})