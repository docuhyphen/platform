import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeDocumentCardStyles = makeStyles({

    documentCard: {
        width: "100%",
        padding: tokens.spacingHorizontalL,
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
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
            padding: tokens.spacingHorizontalM,
            gap: tokens.spacingHorizontalMNudge,
        },
        '@media (max-width: 390px)': {
            padding: tokens.spacingHorizontalM,
        },
    },

    documentError: {
        marginBottom: tokens.spacingVerticalMNudge,
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
        gap: tokens.spacingHorizontalSNudge,
        alignItems: "normal",
    },

    documentName: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flex: 1,
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        '@media (max-width: 640px)': {
            flexDirection: "column",
            alignItems: "flex-start",
            gap: tokens.spacingHorizontalSNudge,
        },
        '@media (max-width: 390px)': {
            gap: tokens.spacingHorizontalXS,
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
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        borderRadius: tokens.borderRadiusCircular,
        fontSize: "12px",
        lineHeight: "1.3",
        overflowWrap: "anywhere",
    },

    documentActions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexDirection: "column",
    },

    documentActionsLine1: {
        display: "flex",
        gap: tokens.spacingHorizontalM,
        justifyContent: "space-between",
        alignItems: "flex-start",
        flexWrap: "wrap",
        '@media (max-width: 640px)': {
            flexDirection: "column",
            alignItems: "stretch",
            gap: tokens.spacingHorizontalS,
        },
    },

    uploadActions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        '@media (max-width: 640px)': {
            width: "100%",
            display: "grid",
            gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
        },
        '@media (max-width: 350px)': {
            gridTemplateColumns: "1fr",
            gap: tokens.spacingHorizontalSNudge,
        },
    },

    actionButton: {
        '@media (max-width: 640px)': {
            width: "100%",
            justifyContent: "center",
            minHeight: "38px",
            paddingLeft: tokens.spacingHorizontalMNudge,
            paddingRight: tokens.spacingHorizontalMNudge,
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
        padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalS}`,
        background: tokens.colorBrandBackground2,
        borderRadius: tokens.borderRadiusMedium,
    },

    documentActionsLine2: {
        '@media (max-width: 390px)': {
            marginTop: tokens.spacingVerticalXXS,
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
    },

});

