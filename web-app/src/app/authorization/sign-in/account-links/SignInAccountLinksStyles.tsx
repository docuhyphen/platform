import {makeStyles, tokens} from "@fluentui/react-components";

export const useSignInAccountLinksStyles = makeStyles({
    accountLinks: {
        marginTop: tokens.spacingVerticalS,
        textAlign: "center",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: tokens.spacingHorizontalL,
    },
});
