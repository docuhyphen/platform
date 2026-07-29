import {makeStyles, tokens} from "@fluentui/react-components";

export const useIndustryDocumentPreviewStyles = makeStyles({
    documentCanvas: {
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "center",
        minHeight: "21rem",
        flex: 1,
        paddingTop: tokens.spacingVerticalXL,
        paddingRight: tokens.spacingHorizontalM,
        paddingBottom: tokens.spacingVerticalM,
        paddingLeft: tokens.spacingHorizontalM,
        overflow: "hidden",
        backgroundColor: tokens.colorNeutralBackground2,
    },

    documentPreview: {
        display: "block",
        width: "auto",
        height: "36rem",
        maxWidth: "100%",
        boxShadow: tokens.shadow8,
        transform: "translateY(-3.5rem)",
        backgroundColor: tokens.colorNeutralBackground1,
        objectFit: "contain",
    },
});
