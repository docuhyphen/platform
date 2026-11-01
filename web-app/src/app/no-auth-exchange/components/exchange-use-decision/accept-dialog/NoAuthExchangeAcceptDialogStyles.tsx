import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeAcceptDialogStyles = makeStyles({

    acceptDialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        padding: `${tokens.spacingVerticalL} 0`,
        marginBottom: tokens.spacingVerticalL,
    },
});
