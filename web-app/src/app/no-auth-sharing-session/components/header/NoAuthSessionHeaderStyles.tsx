import {makeStyles} from "@fluentui/react-components";

export const useNoAuthSessionHeaderStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        padding: "8px 16px",
        boxShadow: "rgba(0, 0, 0, 0.12) 0px 0px 2px, rgba(0, 0, 0, 0.14) 0px 2px 4px",
        background: "white",
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