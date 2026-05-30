import {makeStyles} from "@fluentui/react-components";

export const useAuthorizationStyles = makeStyles({
    auth: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        minHeight: "100vh",
        padding: "24px",
        boxSizing: "border-box",
        background: "#f3f3f3",

        "@media (max-width: 768px)": {
            padding: "12px",
        },
    },
    authSection: {
        display: "flex",
        alignItems: "stretch",
        justifyContent: "center",
        height: "auto",
        flexDirection: "row",
        maxWidth: "100%",
        width: "800px",
        minHeight: "650px",
        borderRadius: "16px",
        boxShadow: "15px 15px 15px rgba(0, 0, 0, .1)",
        background: "rgb(255, 255, 255)",

        "@media (max-width: 768px)": {
            minHeight: "unset",
        },
    },
    authSection1: {
        borderRadius: "16px 0 0 16px",
        display: "flex",
        flexDirection: "column",
        height: "auto",
        boxSizing: "border-box",
        flex: 1,
        padding: "38px",
        maxWidth: "50%",

        "@media (max-width: 768px)": {
            maxWidth: "100%",
        },
    },
    authSection2: {
        borderRadius: "0 16px 16px 0",
        background: "#4b6496",
        color: "rgba(255, 255, 255, .9)",
        height: "auto",
        boxSizing: "border-box",
        flex: 1,
        padding: "38px",
        maxWidth: "50%",

        "@media (max-width: 768px)": {
            display: "none",
        },
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
        flex: "1",
        justifyContent: "center",
        overflowY: "auto",
        minHeight: 0,
    },
});