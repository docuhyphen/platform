import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateVersionListStyles = makeStyles({
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
    },
    row: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    retired: {
        color: tokens.colorNeutralForeground3,
    },
});
