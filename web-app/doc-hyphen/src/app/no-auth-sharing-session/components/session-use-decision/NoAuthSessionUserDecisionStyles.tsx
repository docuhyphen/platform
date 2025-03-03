import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthSessionDocumentListStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        padding: "48px",
        gap: "16px",
        maxWidth: "600px",
        margin: "0 auto",
        boxShadow: "1px 1px 0px 1px rgba(0, 0, 0, .1)",
        borderRadius: "3px",
        marginTop: "48px",
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
        fontSize: "20px",
    }
})