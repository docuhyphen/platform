import {makeStyles} from "@fluentui/react-components";

export const useNoAuthSharingSessionStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%",
        background: "#f9f9f9",
    },

    sessionLoadingContainer:{
        display: "flex",
        width: "100%",
        height: "100%",
        justifyContent: "center",
        alignItems: "center",
    },
})