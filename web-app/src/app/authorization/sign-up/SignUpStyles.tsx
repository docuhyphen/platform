import {tokens, makeStyles} from "@fluentui/react-components";

export const useSignUpStyles = makeStyles({
    signUpCompletionForm: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },
    authHasAccount: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },
    signUpSuccessfulSection: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
        alignItems: "center",
        justifyContent: "center",
        flex: 1,
    },
    validatingSpinnerContainer: {
        display: "flex",
        justifyContent: "center",
        padding: `${tokens.spacingVerticalM} 0`,
    },
});