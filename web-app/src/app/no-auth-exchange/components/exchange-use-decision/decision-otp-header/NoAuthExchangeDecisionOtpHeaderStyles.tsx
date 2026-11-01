import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeDecisionOtpHeaderStyles = makeStyles({

    otpInputGroup: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
    },

    otpInput: {
        width: "38px",
        textAlign: "center",
        fontSize: tokens.fontSizeBase500,
    },

    helperText: {
        color: tokens.colorNeutralForeground3,
    },
});
