import {makeStyles} from "@fluentui/react-components";

export const useNoAuthSessionHeaderStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        padding: "8px 16px",
        boxShadow: "1px 1px 0px 1px rgba(0, 0, 0, .1)",
        background: "white",
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