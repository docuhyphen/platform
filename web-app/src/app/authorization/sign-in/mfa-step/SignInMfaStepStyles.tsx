import {makeStyles, tokens} from "@fluentui/react-components";

export const useSignInMfaStepStyles = makeStyles({
    codeField: {
        marginBottom: tokens.spacingVerticalM,
    },
    actionRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    verifyButton: {
        marginLeft: "auto",
    },
});
