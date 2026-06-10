import {makeStyles} from "@fluentui/react-components";

export const useAppSettingsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        width: "100%",
        minWidth: 0,
    },
    header: {

    },
    dataContainer: {
        display: "flex",
        flexDirection: "row",
        flexWrap: "wrap",
        gap: "16px"
    },

    dataName: {
        minWidth: "150px"
    },

    mainDivider: {
        width: "300px",
        maxWidth: "100%",
        "@media (max-width: 768px)": {
            width: "100%",
        },
    }
});