import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeDocumentListStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        padding: `calc(${tokens.spacingHorizontalXXXL} + ${tokens.spacingHorizontalL})`,
        gap: tokens.spacingHorizontalL,
        maxWidth: "680px",
        width: "100%",
        margin: "0 auto",
        boxShadow: tokens.shadow4,
        borderRadius: tokens.borderRadiusMedium,
        background: tokens.colorNeutralBackground1,
    },

    decisionActions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
    },

    termsAndConditions: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        marginTop: tokens.spacingVerticalXXXL,
    },

    declineButton: {
        '&:hover': {
            color: tokens.colorStatusDangerForeground1
        }
    },

    declineDialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        padding: `${tokens.spacingVerticalL} 0`,
        marginBottom: tokens.spacingVerticalL,
    },

    acceptDialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        padding: `${tokens.spacingVerticalL} 0`,
        marginBottom: tokens.spacingVerticalL,
    },

    otpInputGroup: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
    },

    otpInput: {
        width: "38px",
        textAlign: "center",
        fontSize: "20px",    },

    helperText: {
        color: tokens.colorNeutralForeground3,
    },

    srOnly: {
        position: "absolute",
        width: "1px",
        height: "1px",
        padding: 0,
        margin: "-1px",
        overflow: "hidden",
        clip: "rect(0, 0, 0, 0)",
        border: 0,
    },
})