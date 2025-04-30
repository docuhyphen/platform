import {makeStyles, tokens} from "@fluentui/react-components";

export const useAppSettingsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px"
    },

    dataContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px"
    },

    dataName: {
        minWidth: "150px"
    }
});