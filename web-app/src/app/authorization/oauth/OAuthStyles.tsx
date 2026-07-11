import {tokens, makeStyles} from "@fluentui/react-components";

export const useOAuthStyles = makeStyles({
    oauthCallbackContainer: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100vh",
        gap: tokens.spacingHorizontalL,
        padding: tokens.spacingHorizontalXXXL,
        maxWidth: "480px",
        margin: "0 auto",
    },
    oauthSpinnerContainer: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100vh",
        gap: tokens.spacingHorizontalL,
    },
    oauthMessageBar: {
        width: "100%",
    },
});
