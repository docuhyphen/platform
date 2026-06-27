import {makeStyles} from "@fluentui/react-components";

export const useSignInStyles = makeStyles({
    signInSection: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        height: "100%",
    },
    signInCard: {
        width: "380px",
    },
    signInOptions: {
        display: "flex",
        justifyContent: "space-between",
    },
    authNoAccount: {
        marginTop: "8px",
        textAlign: "center",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: "16px",
    },
    orgPickerList: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        marginTop: "8px",
    },
    orgPickerButton: {
        justifyContent: "flex-start",
    },
});