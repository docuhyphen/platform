import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthSessionDocumentListStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        padding: "48px",
        gap: "16px",
        maxWidth: "680px",
        width: "100%",
        margin: "0 auto",
        boxShadow: "rgba(0, 0, 0, 0.12) 0px 0px 2px, rgba(0, 0, 0, 0.14) 0px 2px 4px",
        borderRadius: "3px",
        background: "white",
    },

    decisionActions: {
        display: "flex",
        gap: "8px",
    },

    termsAndConditions: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        marginTop: "36px",
    },

    declineButton: {
        '&:hover': {
            color: tokens.colorStatusDangerForeground1
        }
    },

    declineDialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        padding: "16px 0",
        marginBottom: "16px",
    },

    acceptDialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        padding: "16px 0",
        marginBottom: "16px",
    },

    otpInputGroup: {
        display: "flex",
        gap: "8px",
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