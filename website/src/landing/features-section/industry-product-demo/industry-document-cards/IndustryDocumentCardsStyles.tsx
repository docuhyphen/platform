import {makeStyles, tokens} from "@fluentui/react-components";

export const useIndustryDocumentCardsStyles = makeStyles({
    documentCards: {
        display: "flex",
        gap: tokens.spacingHorizontalM,
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalXXS}`,
        marginRight: tokens.spacingHorizontalM,
        marginLeft: tokens.spacingHorizontalM,
        overflowX: "auto",
        overflowY: "hidden",
        scrollSnapType: "x proximity",
        backgroundColor: tokens.colorNeutralBackground1,
        scrollbarWidth: "none",

        "&::-webkit-scrollbar": {
            display: "none",
        },
    },

    documentCard: {
        width: "300px",
        minWidth: "300px",
        maxWidth: "300px",
        flex: "0 0 300px",
        scrollSnapAlign: "start",
        boxSizing: "border-box",
        gap: tokens.spacingVerticalXS,
        paddingTop: tokens.spacingVerticalM,
        paddingRight: tokens.spacingHorizontalM,
        paddingBottom: tokens.spacingVerticalM,
        paddingLeft: tokens.spacingHorizontalM,
        borderLeftWidth: "5px",
        borderLeftStyle: "solid",
        borderLeftColor: "transparent",
    },

    selectedDocumentCard: {
        borderLeftColor: tokens.colorBrandForeground1,
        backgroundColor: tokens.colorNeutralBackground1Selected,
        boxShadow: tokens.shadow4,
    },

    documentCardTitle: {
        display: "block",
        width: "100%",
        minWidth: 0,
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
        lineHeight: tokens.lineHeightBase300,
    },

    documentCardFooter: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    documentCardStatus: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
        color: tokens.colorNeutralForeground2,
    },

    uploadedIcon: {
        color: tokens.colorPaletteGreenForeground1,
        flexShrink: 0,
    },

    uploadDate: {
        color: tokens.colorNeutralForeground3,
        whiteSpace: "nowrap",
    },

    documentCardActions: {
        display: "flex",
        alignItems: "center",
        flexShrink: 0,
        gap: tokens.spacingHorizontalXXS,
    },
});
