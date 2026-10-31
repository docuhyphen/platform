import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsErrorStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: tokens.spacingVerticalM,
        padding: tokens.spacingVerticalL,
        flex: 1,
        minWidth: 0,
        textAlign: "center",
    },
});
