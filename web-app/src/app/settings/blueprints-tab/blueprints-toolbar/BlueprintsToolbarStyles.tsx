import {makeStyles, tokens} from "@fluentui/react-components";

export const useBlueprintsToolbarStyles = makeStyles({
    searchRow: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        width: "100%",
        "@media screen and (max-width: 600px)": {
            alignItems: "stretch",
            flexDirection: "column",
        },
    },
    searchRowInputs: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingVerticalS,
        flex: "0 1 auto",
        minWidth: 0,
        "@media screen and (max-width: 600px)": {
            width: "100%",
        },
    },
    searchField: {
        flex: "0 1 420px",
        minWidth: 0,
        width: "420px",
        maxWidth: "100%",
        "@media screen and (max-width: 600px)": {
            flex: 1,
            width: "auto",
        },
    },
});
