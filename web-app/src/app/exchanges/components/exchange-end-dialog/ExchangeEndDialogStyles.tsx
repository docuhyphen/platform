import {tokens, makeStyles} from "@fluentui/react-components";

export const useExchangeEndDialogStyles = makeStyles({
    dialogContentContainer: {
        margin: `${tokens.spacingVerticalS} 0`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL
    },
});