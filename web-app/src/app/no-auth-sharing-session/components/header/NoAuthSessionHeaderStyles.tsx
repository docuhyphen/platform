import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthSessionHeaderStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        padding: "8px 16px",
        boxShadow: tokens.shadow4,
        background: tokens.colorNeutralBackground1,
        position: "fixed",
        width: "100%",
        boxSizing: "border-box",
    },

    signInButtonContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
        alignItems: "center",
    },
    signInButton: {
        display: "flex",
        flexDirection: "row",
        gap: "4px"
    }
})