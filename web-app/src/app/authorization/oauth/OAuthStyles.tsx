import {makeStyles} from "@fluentui/react-components";

export const useOAuthStyles = makeStyles({
    oauthCallbackContainer: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100vh",
        gap: "16px",
        padding: "32px",
        maxWidth: "480px",
        margin: "0 auto",
    },
    oauthSpinnerContainer: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100vh",
        gap: "16px",
    },
    oauthMessageBar: {
        width: "100%",
    },
});
