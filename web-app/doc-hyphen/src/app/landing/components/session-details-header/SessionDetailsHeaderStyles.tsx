import {makeStyles} from "@fluentui/react-components";

export const useSessionDetailsHeaderStyles = makeStyles({

    header: {
        display: "flex",
        flexDirection: "column",
        flex: "1",
    },

    headerLine1: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between"
    },

    headerLine1_2: {
        display: "flex",
        flexDirection: "row",
        gap: "8px"
    },

    headerLine2: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between"
    },

    headerLine3: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between"
    }
});