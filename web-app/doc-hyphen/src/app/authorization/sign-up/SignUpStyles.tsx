import {makeStyles} from "@fluentui/react-components";

export const useSignUpStyles = makeStyles({
    signUpCompletionForm: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
    },
    authHasAccount: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        gap: "8px",
    },
    signUpSuccessfulSection: {
        display: "flex",
        flexDirection: "column",
        gap: "20px",
        alignItems: "center",
        justifyContent: "center",
    },
});