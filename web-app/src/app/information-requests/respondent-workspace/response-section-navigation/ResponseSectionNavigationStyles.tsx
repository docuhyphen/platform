import {makeStyles, tokens} from "@fluentui/react-components";

export const useResponseSectionNavigationStyles = makeStyles({
    navigation: {
        position: "sticky",
        top: 0,
        alignSelf: "start",
        "@media (max-width: 900px)": {
            position: "static",
        },
    },
    list: {
        listStyleType: "none",
        marginTop: 0,
        marginBottom: 0,
        paddingLeft: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        "@media (max-width: 900px)": {
            flexDirection: "row",
            flexWrap: "wrap",
            gap: tokens.spacingHorizontalXS,
        },
    },
    entry: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        textAlign: "start",
        width: "100%",
        height: "auto",
        paddingTop: tokens.spacingVerticalXS,
        paddingBottom: tokens.spacingVerticalXS,
    },
    progress: {
        color: tokens.colorNeutralForeground2,
    },
});
