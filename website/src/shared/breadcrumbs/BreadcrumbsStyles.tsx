import {makeStyles, tokens} from "@fluentui/react-components";

export const useBreadcrumbsStyles = makeStyles({
    wrapper: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        fontSize: tokens.fontSizeBase200,
        color: tokens.colorNeutralForeground3,
    },

    link: {
        color: tokens.colorNeutralForeground3,
        textDecorationLine: "none",

        ":hover": {
            color: tokens.colorBrandForeground1,
            textDecorationLine: "underline",
        },
    },

    current: {
        color: tokens.colorNeutralForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    separator: {
        color: tokens.colorNeutralForeground4,
        display: "inline-flex",
    },
});
