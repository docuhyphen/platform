import {makeStyles} from "@fluentui/react-components";

export const useNoAuthSharingSessionStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%",
        background: "#f9f9f9",
    },

    sessionLoadingContainer: {
        display: "flex",
        width: "100%",
        height: "100%",
        justifyContent: "center",
        alignItems: "center",
    },

    sessionContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        flex: 1,
        maxWidth: "680px",
        width: "100%",
        margin: "0 auto",
        marginTop: "48px",
        padding: "16px",
        boxSizing: "border-box",
    },

    sessionName: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        background: "white",
        padding: "16px",
        boxShadow: "rgba(0, 0, 0, 0.12) 0px 0px 2px, rgba(0, 0, 0, 0.14) 0px 2px 4px",
        marginTop: "48px",
        borderRadius: "4px",
    }
})