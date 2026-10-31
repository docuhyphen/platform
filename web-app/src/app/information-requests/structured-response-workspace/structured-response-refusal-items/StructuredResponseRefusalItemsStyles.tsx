import {makeStyles, tokens} from "@fluentui/react-components";

export const useStructuredResponseRefusalItemsStyles = makeStyles({
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
    },
    item: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "baseline",
        columnGap: tokens.spacingHorizontalS,
        minWidth: 0,
    },
    detail: {
        color: tokens.colorNeutralForeground2,
        overflowWrap: "anywhere",
    },
});
