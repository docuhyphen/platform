import {makeStyles, tokens} from "@fluentui/react-components";

export const useHelpDocumentationNavStyles = makeStyles({
    nav: {
        flex: 1,
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
    },
    navArticleItem: {
        display: "flex",
        justifyContent: "flex-start",
        width: "100%",
        maxWidth: "none",
        minWidth: 0,
        textAlign: "left",
        padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalL} ${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalXXXL}`,
        border: "none",
        background: "none",
        cursor: "pointer",
        color: tokens.colorNeutralForeground2,
        fontWeight: tokens.fontWeightRegular,
        lineHeight: tokens.lineHeightBase300,
        ":hover": {
            backgroundColor: tokens.colorNeutralBackground1Hover,
            color: tokens.colorNeutralForeground1,
        },
    },
    navArticleItemActive: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },
    searchResultItem: {
        flexDirection: "column",
        alignItems: "flex-start",
    },
    navArticleList: {
        display: "flex",
        flexDirection: "column",
        paddingBottom: tokens.spacingVerticalXS,
    },
    searchContainer: {
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalL}`,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        flexShrink: 0,
    },
    searchInput: {
        width: "100%",
    },
    searchResultTitle: {
        display: "block",
        fontSize: tokens.fontSizeBase300,
    },
    searchResultSection: {
        display: "block",
        fontSize: tokens.fontSizeBase200,
        color: tokens.colorNeutralForeground4,
        marginTop: tokens.spacingVerticalXXS,
    },
    searchNoResults: {
        display: "block",
        padding: tokens.spacingHorizontalL,
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase300,
        textAlign: "center",
    },
});
