import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsHeaderTitleStyles = makeStyles({
    headerTitleGroup: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        flex: 1,
        minWidth: 0,
    },

    headerTitleText: {
        display: "flex",
        alignItems: "center",
        flex: 1,
        minWidth: 0,
        overflow: "hidden",
        "& > *": {
            minWidth: 0,
            maxWidth: "100%",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
        },
        "@media (max-width: 768px)": {
            "& > *": {
                fontSize: tokens.fontSizeBase400,
                lineHeight: tokens.lineHeightBase400,
            },
        },
        "@media (max-width: 480px)": {
            "& > *": {
                fontSize: tokens.fontSizeBase300,
                lineHeight: tokens.lineHeightBase300,
            },
        },
    },

    backToListButton: {
        flexShrink: 0,
    },
});
