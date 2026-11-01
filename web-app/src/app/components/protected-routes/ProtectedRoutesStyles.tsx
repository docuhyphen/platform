import {makeStyles} from "@fluentui/react-components";

export const useProtectedRoutesStyles = makeStyles({
    appLayout: {
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "row",
        overflow: "hidden",
    },
    appPane: {
        flex: 1,
        minWidth: 0,
        height: "100%",
        position: "relative",
        transform: "translateZ(0)",
    },
    pageContent: {
        height: "100%",
    },
});
