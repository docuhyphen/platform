import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeDocumentListStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        gap: "14px",
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
        color: tokens.colorNeutralForeground2,
        padding: "10px 12px",
        background: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusLarge,
        lineHeight: "1.5",
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
        gap: "12px",
        background: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusLarge,
        boxShadow: "none",
        transitionProperty: "border-color, box-shadow, transform",
        transitionDuration: "160ms",
        ':hover': {
            borderTopColor: tokens.colorBrandStroke1,
            borderRightColor: tokens.colorBrandStroke1,
            borderBottomColor: tokens.colorBrandStroke1,
            borderLeftColor: tokens.colorBrandStroke1,
            boxShadow: tokens.shadow8,
            transform: "translateY(-1px)",
        },
        '@media (max-width: 640px)': {
            padding: "14px",
            gap: "10px",
        },
        '@media (max-width: 390px)': {
            padding: "12px",
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
        gap: "6px",
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
            flexDirection: "column",
            alignItems: "flex-start",
            gap: "6px",
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
        display: "inline-flex",
        width: "fit-content",
        color: tokens.colorNeutralForeground2,
        background: tokens.colorNeutralBackground3,
        padding: "3px 8px",
        borderRadius: tokens.borderRadiusCircular,
        fontSize: "12px",
        lineHeight: "1.3",
        overflowWrap: "anywhere",
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
            gap: "8px",
        },
    },

    uploadActions: {
        display: "flex",
        gap: "8px",
        flexWrap: "wrap",
        '@media (max-width: 640px)': {
            width: "100%",
            display: "grid",
            gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
        },
        '@media (max-width: 350px)': {
            gridTemplateColumns: "1fr",
            gap: "6px",
        },
    },

    actionButton: {
        '@media (max-width: 640px)': {
            width: "100%",
            justifyContent: "center",
            minHeight: "38px",
            paddingLeft: "10px",
            paddingRight: "10px",
        },
        '@media (max-width: 350px)': {
            minHeight: "36px",
            fontSize: "12px",
        },
    },

    downloadAction: {
        '@media (max-width: 640px)': {
            width: "100%",
            background: tokens.colorNeutralBackground2,
        },
    },

    fileNameText: {
        overflowWrap: "anywhere",
        color: tokens.colorBrandForeground1,
        padding: "6px 8px",
        background: tokens.colorBrandBackground2,
        borderRadius: tokens.borderRadiusMedium,
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
