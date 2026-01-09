import {makeStyles, tokens} from "@fluentui/react-components";

export const useProfileTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "24px"
    },

    dataContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px"
    },

    dataEditable: {
        display: "flex",
        gap: "8px"
    },

    mainDivider: {
        width: "300px"
    }
});