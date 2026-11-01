import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeDeclineDialogStyles = makeStyles({

    declineDialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        padding: `${tokens.spacingVerticalL} 0`,
        marginBottom: tokens.spacingVerticalL,
    },

    helperText: {
        color: tokens.colorNeutralForeground3,
    },
});
