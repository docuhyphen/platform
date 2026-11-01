import {makeStyles, tokens} from "@fluentui/react-components";

export const useIndustryDocumentToolbarStyles = makeStyles({
    toolbar: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        minHeight: "3rem",
        padding: tokens.spacingHorizontalS,
        marginBottom: tokens.spacingVerticalS,
        borderTop: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
        boxSizing: "border-box",
    },

    previewHeaderActions: {
        display: "flex",
        flexDirection: "row-reverse",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
        maxWidth: "100%",
        minWidth: 0,
    },

    pagesInputContainer: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXXS,
        flexShrink: 0,
    },

    pagesInput: {
        "& input": {
            width: "50px",
            textAlign: "right",
        },
    },

    pagesInputAfter: {
        color: tokens.colorNeutralForeground2,
        whiteSpace: "nowrap",
    },

    dividerFullHeight: {
        height: "100%",
    },
});
