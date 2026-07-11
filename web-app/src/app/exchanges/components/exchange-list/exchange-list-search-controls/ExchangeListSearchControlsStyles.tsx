import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeListSearchControlsStyles = makeStyles({

    container: {
        display: "flex",
        gap: tokens.spacingHorizontalXS,
        height: "100%",
        width: "100%",
        boxSizing: "border-box"
    },
    searchField: {
        flex: 1,
        paddingBottom: tokens.spacingVerticalS
    }
});