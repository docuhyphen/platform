import {makeStyles} from "@fluentui/react-components";

export const useAuthorizationStyles = makeStyles({
    auth: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        height: "100%",
        background: "#f3f3f3",
    },
    authSection: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        height: "100%",
        flexDirection: "row",
        maxWidth: "100%",
        width: "800px",
        margin: "80px",
        maxHeight: "600px",
        borderRadius: "16px",
        boxShadow: "15px 15px 15px rgba(0, 0, 0, .1)",
        background: "rgb(255, 255, 255)",
    },
    authSection1: {
        borderRadius: "16px 0 0 16px",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
    },
    authSection2: {
        borderRadius: "0 16px 16px 0",
        background: "#4b6496",
        color: "rgba(255, 255, 255, .9)",
    },
    commonAuthSection: {
        height: "100%",
        boxSizing: "border-box",
        flex: 1,
        padding: "38px",
        maxWidth: "50%",
    },
    authorizationFormSection: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
    },
});